// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk9.stringconcatfactory;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class StringConcatFactoryArtArrayTest extends StringConcatFactoryTestBase {

  private static Pattern EXPECTED =
      Pattern.compile(
          "^Values: \\[Z@[0-9a-f]+, \\[C@[0-9a-f]+, \\[B@[0-9a-f]+, \\[S@[0-9a-f]+, \\[I@[0-9a-f]+,"
              + " \\[J@[0-9a-f]+, \\[F@[0-9a-f]+, \\[D@[0-9a-f]+,"
              + " \\[Ljava\\.lang\\.String;@[0-9a-f]+, \\[Ljava\\.lang\\.Object;@[0-9a-f]+$");
  static final Class<?> MAIN = StringConcatFactoryArrayTestClass.class;

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
    checkInvokeDynamic(inspector, MAIN, expectMakeConcatWithConstants, "testArrays");
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
        .inspectStdOut(s -> assertTrue(EXPECTED.matcher(s).matches()));
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
        .assertFailureWithErrorThatThrows(BootstrapMethodError.class);
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
        // TODO(b/576559667): ART implementation does not support Object types.
        .applyIf(
            parameters.isCfRuntime(),
            r -> r.assertSuccess().inspectStdOut(s -> assertTrue(EXPECTED.matcher(s).matches())),
            r -> r.assertFailureWithErrorThatThrows(BootstrapMethodError.class));
  }
}
