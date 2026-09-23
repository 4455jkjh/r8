// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial.kotlin;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.KotlinCompileMemoizer;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationKotlinInlineFunctionTest extends KotlinTestBase {

  private static final String PKG =
      PartialCompilationKotlinInlineFunctionTest.class.getPackage().getName();
  private static final String CALLER_NAME = PKG + ".Caller";
  private static final String MAIN_NAME = PKG + ".InlineTestKt";

  private final TestParameters parameters;

  @Parameters(name = "{0}, kotlin: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndLambdaGenerations().build());
  }

  public PartialCompilationKotlinInlineFunctionTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    parameters.assumeCanUseR8Partial();
    KotlinCompiler kotlinc = kotlinParameters.getCompiler();
    testForR8Partial(parameters.getBackend())
        .addProgramFiles(compiledJars.getForConfiguration(kotlinParameters))
        .addProgramFiles(kotlinc.getKotlinStdlibJar())
        .addProgramFiles(kotlinc.getKotlinAnnotationJar())
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .addKeepMainRule(MAIN_NAME)
        .addKeepClassAndMembersRules(CALLER_NAME)
        .setMinApi(parameters)
        .setR8PartialConfiguration(
            partialConfigurationBuilder -> {
              // Put Caller and Main in R8 partition; Inlinee is in D8 partition.
              partialConfigurationBuilder
                  .addJavaTypeIncludePattern(CALLER_NAME)
                  .addJavaTypeIncludePattern(MAIN_NAME);
            })
        .allowDiagnosticMessages()
        .allowUnusedDontWarnPatterns()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), MAIN_NAME)
        .assertSuccessWithOutputLines("inlined!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject callerClass = inspector.clazz(CALLER_NAME);
    assertThat(callerClass, isPresent());
    MethodSubject callMethod = callerClass.uniqueMethodWithOriginalName("call");
    assertThat(callMethod, isPresent());
  }

  private static final KotlinCompileMemoizer compiledJars =
      getCompileMemoizer(
          getKotlinSourceFileFromResources(
              PartialCompilationKotlinInlineFunctionTest.class, "InlineTest"));
}
