// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class D8Framework14082017DesugaredVerificationTest extends CompilationTestBase {
  private static final int MIN_SDK = AndroidApiLevel.N.getMajor();
  private static final String JAR =
      ToolHelper.THIRD_PARTY_DIR + "framework/framework_14082017_desugared.jar";

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesStartingFromIncluding(Version.V7_0_0).build();
  }

  @Test
  public void verifyDebugBuild() throws Exception {
    runAndCheckVerification(
        D8Command.builder()
            .addProgramFiles(Paths.get(JAR))
            .setMode(CompilationMode.DEBUG)
            .setMinApiLevel(MIN_SDK),
        JAR,
        parameters.getDexVm());
  }

  @Test
  public void verifyReleaseBuild() throws Exception {
    runAndCheckVerification(
        D8Command.builder()
            .addProgramFiles(Paths.get(JAR))
            .setMode(CompilationMode.RELEASE)
            .setMinApiLevel(MIN_SDK),
        JAR,
        parameters.getDexVm());
  }
}
