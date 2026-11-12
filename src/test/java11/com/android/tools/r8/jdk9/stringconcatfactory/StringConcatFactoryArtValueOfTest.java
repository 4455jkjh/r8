// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk9.stringconcatfactory;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.internal.StringUtils;
import java.nio.file.Path;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class StringConcatFactoryArtValueOfTest extends StringConcatFactoryTestBase {

  private static final String EXPECTED_LINE = "Values: true, A, 0, 1, 2, 3, 4.0, 5.0";
  static final String EXPECTED =
      StringUtils.lines(EXPECTED_LINE, EXPECTED_LINE, EXPECTED_LINE, EXPECTED_LINE);
  static final Class<?> MAIN = StringConcatFactoryValueOfTestClass.class;

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean makeConcatFromJavac;

  @Parameterized.Parameters(name = "{0}, makeConcatFromJavac: {1}")
  public static List<Object[]> data() {
    return getArtTestParameters();
  }

  private static Path compiledWithMakeConcatJar;

  @BeforeClass
  public static void compileWithMakeConcat() throws Exception {
    compiledWithMakeConcatJar = compileWithMakeConcat(MAIN);
  }

  private void checkInvokeDynamic(CodeInspector inspector, boolean expectMakeConcatWithConstants) {
    checkInvokeDynamic(
        inspector,
        MAIN,
        expectMakeConcatWithConstants,
        "testPrimitivesValueOf",
        "testBoxedPrimitives",
        "testBoxedPrimitivesValueOf",
        "testNestedStringValueOf");
  }

  private void checkValueOf(
      CodeInspector inspector, long expectedSingle, long expectedDoubleNested) {
    checkValueOf(inspector, MAIN, "testPrimitivesValueOf", expectedSingle);
    checkValueOf(inspector, MAIN, "testBoxedPrimitives", expectedSingle);
    checkValueOf(inspector, MAIN, "testBoxedPrimitivesValueOf", expectedDoubleNested);
    checkValueOf(inspector, MAIN, "testNestedStringValueOf", expectedDoubleNested);
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .applyIf(
            makeConcatFromJavac,
            b -> b.addProgramFiles(compiledWithMakeConcatJar),
            b -> b.addProgramClasses(MAIN))
        .run(parameters.getRuntime(), MAIN)
        .inspect(inspector -> checkInvokeDynamic(inspector, !makeConcatFromJavac))
        .inspect(inspector -> checkValueOf(inspector, 8, 16))
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addProgramClasses(MAIN)
        .apply(this::enableStringConcatFactoryForDex)
        .compile()
        .inspect(inspector -> checkInvokeDynamic(inspector, true))
        .run(parameters.getRuntime(), MAIN)
        .inspect(inspector -> checkValueOf(inspector, 0, 0))
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClasses(MAIN)
        // Default is JDK-8 rt.jar which has no definition of StringConcatFactory.
        .applyIf(
            parameters.isCfRuntime(),
            b -> b.addLibraryProvider(JdkClassFileProvider.fromSystemJdk()))
        .setMinApi(parameters)
        .addKeepMainRule(MAIN)
        .apply(this::enableStringConcatFactoryForDex)
        .enableProguardTestOptions()
        .addKeepRules("-neverinline class " + MAIN.getTypeName() + "{ *** test*(...); }")
        .compile()
        .inspect(inspector -> checkInvokeDynamic(inspector, true))
        .run(parameters.getRuntime(), MAIN)
        .inspect(inspector -> checkValueOf(inspector, 0, 0))
        .assertSuccessWithOutput(EXPECTED);
  }
}
