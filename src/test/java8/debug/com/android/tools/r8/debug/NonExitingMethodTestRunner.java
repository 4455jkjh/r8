// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.origin.Origin;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NonExitingMethodTestRunner extends DebugTestBase {

  public static final Class CLASS = NonExitingMethodTest.class;
  public static final String FILE = CLASS.getSimpleName() + ".java";

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesStartingFromIncluding(Version.V6_0_1).build();
  }

  private static Path getClassFilePath() {
    return ToolHelper.getClassFileForTestClass(CLASS);
  }

  public DebugTestConfig cfConfig() {
    return new CfDebugTestConfig(ToolHelper.getClassPathForTests());
  }

  public DebugTestConfig d8Config() {
    return new D8DebugTestConfig(parameters.asDexRuntime()).compileAndAdd(temp, getClassFilePath());
  }

  public DebugTestConfig r8CfConfig() throws CompilationFailedException {
    Path path = temp.getRoot().toPath().resolve("out.jar");
    ToolHelper.runR8(
        R8Command.builder()
            .setMode(CompilationMode.DEBUG)
            .setDisableTreeShaking(true)
            .setDisableMinification(true)
            .addProguardConfiguration(
                ImmutableList.of("-keepattributes SourceFile,LineNumberTable"), Origin.unknown())
            .addProgramFiles(getClassFilePath())
            .setProgramConsumer(new ClassFileConsumer.ArchiveConsumer(path))
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
            .build());
    return new CfDebugTestConfig().addPaths(path);
  }

  @Test
  public void test() throws Exception {
    // TODO(b/199700280): Reenable on 12.0.0 when we have the libjdwp.so file include and the flags
    // fixed.
    Assume.assumeTrue(
        "Skipping test " + testName.getMethodName() + " because debugging not enabled in 12.0.0",
        !parameters.isDexRuntimeVersionNewerThanOrEqual(Version.V12_0_0));
    Assume.assumeTrue(
        "Skipping test "
            + testName.getMethodName()
            + " because debug tests are not yet supported on Windows",
        !ToolHelper.isWindows());
    new DebugStreamComparator()
        .add("CF", streamDebugTest(cfConfig(), CLASS.getCanonicalName(), NO_FILTER))
        .add("D8", streamDebugTest(d8Config(), CLASS.getCanonicalName(), NO_FILTER))
        .add("R8/CF", streamDebugTest(r8CfConfig(), CLASS.getCanonicalName(), NO_FILTER))
        .setFilter(s -> s.getSourceFile().equals(FILE))
        .compare();
  }
}
