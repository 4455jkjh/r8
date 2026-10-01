// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Paths;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class D8Framework14082017VerificationTest extends CompilationTestBase {
  private static final int MIN_SDK = AndroidApiLevel.N.getMajor();
  private static final String JAR = "third_party/framework/framework_14082017.jar";

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().build();
  }

  @Test
  @Ignore("b/259195080")
  public void verifyDebugBuild() throws Exception {
    runAndCheckVerification(
        D8Command.builder()
            .addProgramFiles(Paths.get(JAR))
            .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
            .setMode(CompilationMode.DEBUG)
            .setMinApiLevel(MIN_SDK),
        JAR,
        parameters.getDexVm());
  }

  @Test
  @Ignore("b/259195080")
  public void verifyReleaseBuild() throws Exception {
    runAndCheckVerification(
        D8Command.builder()
            .addProgramFiles(Paths.get(JAR))
            .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
            .setMode(CompilationMode.RELEASE)
            .setMinApiLevel(MIN_SDK),
        JAR,
        parameters.getDexVm());
  }
}
