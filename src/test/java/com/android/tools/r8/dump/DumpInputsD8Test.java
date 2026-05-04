// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dump;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.DumpInputFlags;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.internal.FileUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DumpInputsD8Test extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withSystemRuntime().build();
  }

  @Test
  public void testD8() throws Exception {
    Path dump = temp.newFolder().toPath().resolve("dump.zip");
    testForD8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
        .addOptionsModification(
            options -> options.setDumpInputFlags(DumpInputFlags.dumpToFile(dump)))
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertInfosMatch(
                    diagnosticMessage(containsString("Dumped compilation inputs to:"))));
    verifyDump(dump, null);
  }

  @Test
  public void testD8WithProguardMapInputAndOutput() throws Exception {
    Path dump = temp.newFolder().toPath().resolve("dump.zip");
    Path proguardMapInputFile = temp.newFolder().toPath().resolve("proguard.map");
    List<String> proguardMapInputFileContent = ImmutableList.of("# Hello, mapping input.");
    FileUtils.writeTextFile(proguardMapInputFile, proguardMapInputFileContent);
    testForD8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
        .apply(
            b ->
                b.getBuilder()
                    .setProguardMapInputFile(proguardMapInputFile)
                    .setProguardMapConsumer(StringConsumer.emptyConsumer()))
        .addOptionsModification(
            options -> options.setDumpInputFlags(DumpInputFlags.dumpToFile(dump)))
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertInfosMatch(
                    diagnosticMessage(containsString("Dumped compilation inputs to:"))));
    verifyDump(dump, proguardMapInputFileContent);
  }

  private void verifyDump(Path dumpFile, List<String> proguardMapInputFileContent)
      throws IOException {
    assertTrue(Files.exists(dumpFile));
    Path unzipped = temp.newFolder().toPath();
    ZipUtils.unzip(dumpFile.toString(), unzipped.toFile());
    assertTrue(Files.exists(unzipped.resolve("r8-version")));
    assertTrue(Files.exists(unzipped.resolve("build.properties")));
    assertTrue(Files.exists(unzipped.resolve("program.jar")));
    assertTrue(Files.exists(unzipped.resolve("library.jar")));
    assertTrue(Files.exists(unzipped.resolve("classpath.jar")));
    Path proguardInputMap = unzipped.resolve("proguard_input.map");
    assertEquals(proguardMapInputFileContent != null, Files.exists(proguardInputMap));
    if (proguardMapInputFileContent != null) {
      assertEquals(proguardMapInputFileContent, FileUtils.readAllLines(proguardInputMap));
    }
    assertEquals(
        proguardMapInputFileContent != null,
        FileUtils.readAllLines(unzipped.resolve("build.properties"))
            .contains("proguard-map-output=true"));
    Set<String> entries = new HashSet<>();
    ZipUtils.iter(
        unzipped.resolve("program.jar").toString(), (entry, input) -> entries.add(entry.getName()));
    assertTrue(
        entries.contains(
            DescriptorUtils.getClassFileName(
                DescriptorUtils.javaTypeToDescriptor(TestClass.class.getTypeName()))));
  }

  static class TestClass {
    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }
}
