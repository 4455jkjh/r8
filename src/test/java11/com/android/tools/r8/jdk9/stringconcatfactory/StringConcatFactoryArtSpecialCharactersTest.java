// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk9.stringconcatfactory;

import static org.hamcrest.CoreMatchers.containsString;

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
public class StringConcatFactoryArtSpecialCharactersTest extends StringConcatFactoryTestBase {

  static final String EXPECTED = StringUtils.lines("Special: \1a\2c\1\2");
  static final Class<?> MAIN = StringConcatFactorySpecialCharactersTestClass.class;

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

  private void checkInvokeDynamic(
      CodeInspector inspector,
      boolean expectMakeConcatWithConstants,
      String... expectedBootstrapArgs) {
    checkInvokeDynamic(
        inspector,
        MAIN,
        expectMakeConcatWithConstants,
        "testSpecialCharacters",
        expectedBootstrapArgs);
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
        .inspect(
            inspector ->
                checkInvokeDynamic(
                    inspector, !makeConcatFromJavac, "\2\1\2\1\2", "Special: \1", "\2", "\1\2"))
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addProgramClasses(MAIN)
        .apply(this::enableStringConcatFactoryForDex)
        .compile()
        // TODO(b/576559667): The recipe should not inline \1 and \2 from constants.
        .inspect(inspector -> checkInvokeDynamic(inspector, true, "Special: \1\1\2\1\1\2"))
        .run(parameters.getRuntime(), MAIN)
        // TODO(b/576559667): This should work.
        .assertFailureWithErrorThatMatches(
            containsString("StringConcatException: Unsupported extra arguments"));
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
        // TODO(b/576559667): The recipe should not inline \1 and \2 from constants.
        .inspect(inspector -> checkInvokeDynamic(inspector, true, "Special: \1\1\2\1\1\2"))
        .run(parameters.getRuntime(), MAIN)
        // TODO(b/576559667): This should work (both on CF and ART).
        .assertFailureWithErrorThatMatchesIf(
            parameters.isDexRuntime(),
            containsString("StringConcatException: Unsupported extra arguments"));
  }
}
