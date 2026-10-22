// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dump;

import static com.android.tools.r8.MarkerMatcher.assertMarkersMatch;
import static com.android.tools.r8.MarkerMatcher.markerMinApi;
import static com.android.tools.r8.MarkerMatcher.markerTool;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.CompileDumpCompatR8;
import com.android.tools.r8.utils.CompileDumpD8;
import com.android.tools.r8.utils.CompileDumpR8Assistant;
import com.android.tools.r8.utils.DumpInputFlags;
import com.android.tools.r8.utils.ExtractMarkerUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CompileDumpTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public AndroidApiLevel apiLevel;

  @Parameters(name = "{0}, api: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withNoneRuntime().build(),
        ImmutableList.of(AndroidApiLevel.BAKLAVA, AndroidApiLevel.BAKLAVA_1));
  }

  @Test
  public void testCompileDumpD8() throws Exception {
    Path dumpFile = temp.newFile("dump.zip").toPath();
    testForD8(Backend.DEX)
        .addProgramClasses(TestClass.class)
        .setMinApi(apiLevel)
        .addOptionsModification(
            options -> options.setDumpInputFlags(DumpInputFlags.dumpToFile(dumpFile)))
        .compile();

    CompilerDump dump = CompilerDump.fromArchive(dumpFile, temp.newFolder().toPath());
    assertEquals(apiLevel.asUnchecked(), dump.getBuildProperties().getMinApi());

    Path outputJar = temp.newFile("out.jar").toPath();
    CompileDumpD8.main(
        new String[] {
          dump.getProgramArchive().toString(),
          "--output",
          outputJar.toString(),
          "--lib",
          dump.getLibraryArchive().toString(),
          "--classpath",
          dump.getClasspathArchive().toString(),
          "--min-api",
          dump.getBuildProperties().getMinApi().toString()
        });

    assertMarkersMatch(
        ExtractMarkerUtils.extractMarkersFromFile(outputJar),
        allOf(markerTool(Tool.D8), markerMinApi(apiLevel)));

    verifyCrossVersionCompileDump(dumpFile, Tool.D8);
  }

  @Test
  public void testCompileDumpCompatR8() throws Exception {
    Path dumpFile = temp.newFile("dump.zip").toPath();
    testForR8(Backend.DEX)
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .allowDiagnosticInfoMessages()
        .setMinApi(apiLevel)
        .addOptionsModification(
            options -> options.setDumpInputFlags(DumpInputFlags.dumpToFile(dumpFile)))
        .compile();

    CompilerDump dump = CompilerDump.fromArchive(dumpFile, temp.newFolder().toPath());
    assertEquals(apiLevel.asUnchecked(), dump.getBuildProperties().getMinApi());

    Path outputJar = temp.newFile("out.jar").toPath();
    CompileDumpCompatR8.main(
        new String[] {
          dump.getProgramArchive().toString(),
          "--output",
          outputJar.toString(),
          "--lib",
          dump.getLibraryArchive().toString(),
          "--classpath",
          dump.getClasspathArchive().toString(),
          "--pg-conf",
          dump.getProguardConfigFile().toString(),
          "--min-api",
          dump.getBuildProperties().getMinApi().toString()
        });

    assertMarkersMatch(
        ExtractMarkerUtils.extractMarkersFromFile(outputJar),
        allOf(markerTool(Tool.R8), markerMinApi(apiLevel)));

    verifyCrossVersionCompileDump(dumpFile, Tool.R8);
  }

  private void verifyCrossVersionCompileDump(Path dumpFile, Tool tool) throws Exception {
    verifyCrossVersionCompileDump(
        dumpFile, tool, System.getProperty("java.class.path"), true, apiLevel);

    Path r8JarWithoutMinorSupport =
        Paths.get(ToolHelper.THIRD_PARTY_DIR, "r8", "r8lib_8.2.20-dev.jar").toAbsolutePath();
    // R8 8.2.20-dev predates minor API levels and clamps API levels > 34 to AndroidApiLevel.U (34).
    verifyCrossVersionCompileDump(
        dumpFile, tool, r8JarWithoutMinorSupport.toString(), false, AndroidApiLevel.U);
  }

  private void verifyCrossVersionCompileDump(
      Path dumpFile,
      Tool tool,
      String r8Jar,
      boolean supportsMinorApiLevel,
      AndroidApiLevel expectedApiLevel)
      throws Exception {
    Path compileDumpTemp = temp.newFolder().toPath();
    Path outputJar = compileDumpTemp.resolve("out.jar");
    ProcessBuilder builder =
        new ProcessBuilder(
            "tools/compiledump.py",
            "-d",
            dumpFile.toString(),
            "--r8-jar",
            r8Jar,
            "--temp",
            compileDumpTemp.toString(),
            "--no-build",
            "-D" + InternalOptions.ALLOW_MIN_API_MINOR_LEVELS_PROPERTY_KEY + "=true");
    builder.directory(Paths.get(ToolHelper.THIRD_PARTY_DIR).toAbsolutePath().getParent().toFile());
    ProcessResult result = ToolHelper.runProcess(builder);
    assertEquals(result.toString(), 0, result.exitCode);
    if (!supportsMinorApiLevel && apiLevel.getMinor() != 0) {
      assertThat(
          result.stdout,
          containsString("Minor API level not supported, falling back to major API level."));
    } else {
      assertThat(
          result.stdout,
          not(containsString("Minor API level not supported, falling back to major API level.")));
    }
    assertMarkersMatch(
        ExtractMarkerUtils.extractMarkersFromFile(outputJar),
        allOf(markerTool(tool), markerMinApi(expectedApiLevel)));
  }

  @Test
  public void testCompileDumpR8Assistant() throws Exception {
    Path dexInput =
        testForD8(Backend.DEX)
            .addProgramClasses(TestClass.class)
            .setMinApi(apiLevel)
            .compile()
            .writeToZip();

    Path outputJar = temp.newFile("out.jar").toPath();
    CompileDumpR8Assistant.main(
        new String[] {
          dexInput.toString(),
          "--output",
          outputJar.toString(),
          "--lib",
          ToolHelper.getAndroidJar(AndroidApiLevel.BAKLAVA).toString(),
          "--min-api",
          apiLevel.getNumericString(),
          "--runtime",
          ToolHelper.ASSISTANT_RUNTIME_DEX.toString()
        });

    assertMarkersMatch(
        ExtractMarkerUtils.extractMarkersFromFile(outputJar),
        allOf(markerTool(Tool.D8), markerMinApi(AndroidApiLevel.B)),
        allOf(markerTool(Tool.D8), markerMinApi(apiLevel)),
        allOf(markerTool(Tool.R8Assistant), markerMinApi(apiLevel)));
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }
}
