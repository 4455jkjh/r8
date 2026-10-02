// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.reflection;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.KotlinCompileMemoizer;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestDeps;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.kotlin.metadata.KotlinMetadataTestBase;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// See b/230369515 for context.
@RunWith(Parameterized.class)
public class ReflectiveConstructionWithInlineClassTest extends KotlinTestBase {

  private final TestParameters parameters;
  private static final String EXPECTED_OUTPUT = "Value(rawValue=0)";
  private static final String PKG =
      ReflectiveConstructionWithInlineClassTest.class.getPackage().getName();
  private static final String KOTLIN_FILE = "ReflectiveConstructionWithInlineClass";
  private static final String MAIN_CLASS = PKG + "." + KOTLIN_FILE + "Kt";

  private static final KotlinCompileMemoizer compiledJars =
      getCompileMemoizer(
          getKotlinSourceFileFromResources(
              DescriptorUtils.getInternalNameFromJavaType(PKG), KOTLIN_FILE));

  private static final BiFunction<KotlinTestParameters, AndroidApiLevel, D8TestCompileResult>
      compiledForD8 =
          memoizeBiFunction(
              (kotlinParameters, apiLevel) -> {
                KotlinCompiler kotlinc = kotlinParameters.getCompiler();
                return testForD8(getStaticTemp())
                    .addProgramFiles(compiledJars.getForConfiguration(kotlinParameters))
                    .addProgramFiles(kotlinc.getKotlinStdlibJar())
                    .addProgramFiles(kotlinc.getKotlinReflectJar())
                    .setMinApi(apiLevel)
                    .enableServiceLoader()
                    .compile();
              });

  private static final BiFunction<KotlinTestParameters, AndroidApiLevel, R8TestCompileResult>
      compiledForR8KeepDataClass =
          memoizeBiFunction(
              (kotlinParameters, apiLevel) ->
                  configureR8(kotlinParameters, apiLevel)
                      .addDontObfuscate()
                      .compile()
                      .assertNoErrorMessages()
                      .apply(
                          KotlinMetadataTestBase
                              ::verifyExpectedWarningsFromKotlinReflectAndStdLib));

  private static final BiFunction<KotlinTestParameters, AndroidApiLevel, R8TestCompileResult>
      compiledForR8KeepDataClassAndInlineClass =
          memoizeBiFunction(
              (kotlinParameters, apiLevel) ->
                  configureR8(kotlinParameters, apiLevel)
                      .addKeepRules("-keep class " + PKG + ".Value { *; }")
                      .compile()
                      .assertNoErrorMessages()
                      .apply(
                          KotlinMetadataTestBase
                              ::verifyExpectedWarningsFromKotlinReflectAndStdLib));

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters()
            // Internal classes are supported from Kotlin 1.5.
            .withCompilersStartingFromIncluding(KotlinCompilerVersion.KOTLINC_1_5_0)
            .withOldCompilersStartingFrom(KotlinCompilerVersion.KOTLINC_1_5_0)
            .build());
  }

  public ReflectiveConstructionWithInlineClassTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  @Test
  public void testCf() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramFiles(compiledJars.getForConfiguration(kotlinParameters))
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinReflectJar())
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    compiledForD8
        .apply(kotlinParameters, parameters.getApiLevel())
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  private static R8FullTestBuilder configureR8(
      KotlinTestParameters kotlinParameters, AndroidApiLevel apiLevel) {
    KotlinCompiler kotlinc = kotlinParameters.getCompiler();
    return testForR8(getStaticTemp(), apiLevel == null ? Backend.CF : Backend.DEX)
        .addProgramFiles(compiledJars.getForConfiguration(kotlinParameters))
        .addProgramFiles(kotlinc.getKotlinStdlibJar())
        .addProgramFiles(kotlinc.getKotlinReflectJar())
        .addProgramFiles(kotlinc.getKotlinAnnotationJar())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST))
        // Add java.lang.invoke.LambdaMetafactory for class file generation.
        .applyIf(apiLevel == null, b -> b.addLibraryFiles(TestDeps.getCoreLambdaStubsJar()))
        .setMinApi(apiLevel)
        .addKeepMainRule(MAIN_CLASS)
        .addKeepClassAndMembersRules(PKG + ".Data")
        .addKeepEnumsRule()
        .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
        .allowDiagnosticMessages()
        .allowUnusedDontWarnKotlinReflectJvmInternal()
        .allowUnusedDontWarnJavaLangClassValue()
        .apply(configureForLibraryWithEmbeddedProguardRules())
        .addOptionsModification(
            options -> options.getTestingOptions().enableVerticalClassMergerLensAssertion = false);
  }

  @Test
  public void testR8KeepDataClass() throws Exception {
    compiledForR8KeepDataClass
        .apply(kotlinParameters, parameters.getApiLevel())
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertFailureWithErrorThatThrows(IllegalArgumentException.class);
  }

  @Test
  public void testR8KeepDataClassAndInlineClass() throws Exception {
    compiledForR8KeepDataClassAndInlineClass
        .apply(kotlinParameters, parameters.getApiLevel())
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }
}
