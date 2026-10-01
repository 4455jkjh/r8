// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.origin.Origin;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InterfaceRenamingTestRunner extends TestBase {

  private static final Class<?> CLASS = InterfaceRenamingTest.class;
  private static final Class<?>[] CLASSES = InterfaceRenamingTest.CLASSES;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultCfRuntime().withDexRuntimes().build();
  }

  @Test
  public void testCfNoMinify() throws Exception {
    testCf(MinifyMode.NONE);
  }

  @Test
  public void testCfMinify() throws Exception {
    testCf(MinifyMode.JAVA);
  }

  @Test
  public void testDexNoMinify() throws Exception {
    testDex(MinifyMode.NONE);
  }

  @Test
  public void testDexMinify() throws Exception {
    testDex(MinifyMode.JAVA);
  }

  private void testCf(MinifyMode minify) throws Exception {
    parameters.assumeCfRuntime();
    ProcessResult runInput =
        ToolHelper.runJava(ToolHelper.getClassPathForTests(), CLASS.getCanonicalName());
    assertEquals(0, runInput.exitCode);
    Path outCf = temp.getRoot().toPath().resolve("cf.zip");
    build(new ClassFileConsumer.ArchiveConsumer(outCf), minify);
    ProcessResult runCf = ToolHelper.runJava(outCf, CLASS.getCanonicalName());
    assertEquals(runInput.toString(), runCf.toString());
  }

  private void testDex(MinifyMode minify) throws Exception {
    parameters.assumeDexRuntime();
    ProcessResult runInput =
        ToolHelper.runJava(ToolHelper.getClassPathForTests(), CLASS.getCanonicalName());
    assertEquals(0, runInput.exitCode);
    Path outDex = temp.getRoot().toPath().resolve("dex.zip");
    build(new DexIndexedConsumer.ArchiveConsumer(outDex), minify);
    ProcessResult runDex =
        ToolHelper.runArtNoVerificationErrorsRaw(
            outDex.toString(), CLASS.getCanonicalName(), parameters.getDexVm());
    assertEquals(runInput.stdout, runDex.stdout);
    assertEquals(runInput.exitCode, runDex.exitCode);
  }

  private void build(ProgramConsumer consumer, MinifyMode minify) throws Exception {
    List<String> config =
        Arrays.asList(
            "-keep public class " + CLASS.getCanonicalName() + " {",
            "  public static void main(...);",
            "}");

    Builder builder =
        ToolHelper.addProguardConfigurationConsumer(
                R8Command.builder(),
                pgConfig -> {
                  pgConfig.enablePrintMapping(null, null, null, null);
                  if (!minify.isMinify()) {
                    pgConfig.disableObfuscation();
                  }
                })
            .setMode(CompilationMode.DEBUG)
            .addLibraryFiles(
                parameters.isCfRuntime()
                    ? ToolHelper.getJava8RuntimeJar()
                    : ToolHelper.getMostRecentAndroidJar())
            .setProgramConsumer(consumer)
            .addProguardConfiguration(config, Origin.unknown());
    for (Class<?> c : CLASSES) {
      builder.addClassProgramData(ToolHelper.getClassAsBytes(c), Origin.unknown());
    }
    ToolHelper.runR8(builder.build());
  }
}
