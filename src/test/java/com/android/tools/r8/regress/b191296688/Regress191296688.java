// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b191296688;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinCompilerTool.KotlinTargetVersion;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.utils.DescriptorUtils;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Regress191296688 extends KotlinTestBase {

  private static final String PKG = Regress191296688.class.getPackage().getName();

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getKotlinTestParameters()
            .withCompilersStartingFromIncluding(KotlinCompilerVersion.KOTLINC_1_5_0)
            .withAllLambdaGenerations()
            .withTargetVersion(KotlinTargetVersion.JAVA_8)
            .withOldCompilersStartingFrom(KotlinCompilerVersion.KOTLINC_1_5_0)
            .build());
  }

  public Regress191296688(TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  @Test
  public void testRegress191296688() throws Exception {
    Path aLib = writeClassesToJar(A.class);
    String folder = DescriptorUtils.getBinaryNameFromJavaType(PKG);
    CfRuntime cfRuntime = TestRuntime.getCheckedInJdk9();
    Path ktClasses =
        kotlinc(cfRuntime, kotlinParameters)
            .addSourceFiles(getKotlinFileInTest(folder, "B"))
            .addClasspathFiles(aLib)
            .compile();
    Path desugaredJar =
        testForD8(Backend.CF)
            .addLibraryFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(ktClasses)
            .addProgramClasses(A.class)
            .setMinApi(parameters)
            .compile()
            .writeToZip();
    testForD8()
        .addProgramFiles(desugaredJar)
        .setMinApi(parameters)
        .disableDesugaring()
        .run(parameters.getRuntime(), PKG + ".BKt")
        .assertSuccessWithOutputLines("hep");
  }
}
