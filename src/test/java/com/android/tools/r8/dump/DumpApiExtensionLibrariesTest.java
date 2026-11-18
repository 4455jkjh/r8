// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dump;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DumpInputFlags;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DumpApiExtensionLibrariesTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void test() throws Exception {
    Path extensionLibrary1 = writeClassesToJar(ExtensionApi1.class);
    Path extensionLibrary2 = writeClassesToJar(ExtensionApi2.class);
    Path dumpFile = temp.newFolder().toPath().resolve("dump.zip");
    testForExternalR8(Backend.DEX)
        .addJvmFlag(
            "-Dcom.android.tools.r8.androidApiExtensionLibraries="
                + extensionLibrary1
                + ","
                + extensionLibrary2)
        .addJvmFlag("-Dcom.android.tools.r8.dumpinputtofile=" + dumpFile)
        .addProgramClasses(Main.class)
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar(), extensionLibrary1, extensionLibrary2)
        .addKeepMainRule(Main.class)
        .setMinApi(AndroidApiLevel.B)
        .compile();
    verifyDump(dumpFile, extensionLibrary1, extensionLibrary2);
  }

  @Test
  public void testBuilderApi() throws Exception {
    Path extensionLibrary1 = writeClassesToJar(ExtensionApi1.class);
    Path extensionLibrary2 = writeClassesToJar(ExtensionApi2.class);
    Path dumpFile = temp.newFolder().toPath().resolve("dump.zip");
    testForR8(Backend.DEX)
        .addProgramClasses(Main.class)
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar(), extensionLibrary1, extensionLibrary2)
        .addKeepMainRule(Main.class)
        .setMinApi(AndroidApiLevel.B)
        .apply(b -> b.getBuilder().addApiExtensionLibraries(extensionLibrary1))
        .apply(b -> b.getBuilder().addApiExtensionLibraries(ImmutableList.of(extensionLibrary2)))
        .addOptionsModification(
            options -> options.setDumpInputFlags(DumpInputFlags.dumpToFile(dumpFile)))
        .allowDiagnosticInfoMessages()
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertInfosMatch(
                    diagnosticMessage(containsString("Dumped compilation inputs to:"))));
    verifyDump(dumpFile, extensionLibrary1, extensionLibrary2);
  }

  private void verifyDump(Path dumpFile, Path extensionLibrary1, Path extensionLibrary2)
      throws Exception {
    CompilerDump dump = CompilerDump.fromArchive(dumpFile, temp.newFolder().toPath());
    List<Path> dumpedLibraries = dump.getApiExtensionLibraries();
    assertEquals(2, dumpedLibraries.size());
    assertArrayEquals(
        Files.readAllBytes(extensionLibrary1), Files.readAllBytes(dumpedLibraries.get(0)));
    assertArrayEquals(
        Files.readAllBytes(extensionLibrary2), Files.readAllBytes(dumpedLibraries.get(1)));
  }

  public static class ExtensionApi1 {}

  public static class ExtensionApi2 {}

  static class Main {

    public static void main(String[] args) {
      System.out.println(new ExtensionApi1());
      System.out.println(new ExtensionApi2());
    }
  }
}
