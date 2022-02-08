// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.jdk8272564;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.examples.jdk18.jdk8272564.Jdk8272564;
import com.android.tools.r8.utils.InternalOptions.TestingOptions;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Jdk8272564Test extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // TODO(b/218293990): Right now the JDK 18 tests are built with -target 17, as our Gradle
    //  version does not know of -target 18.
    // TODO(b/174431251): This should be replaced with .withCfRuntimes(start = jdk17).
    return getTestParameters()
        .withCustomRuntime(TestRuntime.getCheckedInJdk17())
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  @Parameter(0)
  public TestParameters parameters;

  // With the fix for JDK-8272564 there are no invokevirtual instructions.
  private void assertJdk8272564FixedCode(CodeInspector inspector) throws Exception {
    assertTrue(
        inspector
            .clazz(Jdk8272564.Main.typeName())
            .uniqueMethodWithName("f")
            .streamInstructions()
            .noneMatch(InstructionSubject::isInvokeVirtual));
  }

  // Without the fix for JDK-8272564 there is one invokeinterface and 2 invokevirtual instructions.
  private void assertJdk8272564NotFixedCode(CodeInspector inspector) throws Exception {
    assertEquals(
        1,
        inspector
            .clazz(Jdk8272564.Main.typeName())
            .uniqueMethodWithName("f")
            .streamInstructions()
            .filter(InstructionSubject::isInvokeInterface)
            .count());
    assertEquals(
        2,
        inspector
            .clazz(Jdk8272564.Main.typeName())
            .uniqueMethodWithName("f")
            .streamInstructions()
            .filter(InstructionSubject::isInvokeVirtual)
            .count());
  }

  @Test
  // See https://bugs.openjdk.java.net/browse/JDK-8272564.
  public void testJdk8272564Compiler() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    // Ensure that the test is running with CF input from fixing JDK-8272564.
    assertJdk8272564FixedCode(new CodeInspector(Jdk8272564.jar()));
  }

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addRunClasspathFiles(Jdk8272564.jar())
        .run(TestRuntime.getCheckedInJdk17(), Jdk8272564.Main.typeName())
        .assertSuccess();
  }

  @Test
  public void testD8() throws Exception {
    testForDesugaring(parameters)
        .addProgramFiles(Jdk8272564.jar())
        .run(parameters.getRuntime(), Jdk8272564.Main.typeName())
        .applyIf(
            parameters.getRuntime().isDex()
                && parameters.getRuntime().asDex().getVersion().isOlderThanOrEqual(Version.V4_4_4),
            r -> r.assertFailureWithErrorThatThrows(NoSuchMethodError.class),
            parameters.getRuntime().isDex()
                && parameters.getRuntime().asDex().getVersion().isOlderThanOrEqual(Version.V7_0_0),
            r -> r.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class),
            r -> r.assertSuccess());
  }

  @Test
  public void testR8() throws Exception {
    // The R8 lens code rewriter rewrites to the code prior to fixing JDK-8272564.
    testForR8(parameters.getBackend())
        .addProgramFiles(Jdk8272564.jar())
        .setMinApi(parameters.getApiLevel())
        .noTreeShaking()
        .addKeepClassAndMembersRules(Jdk8272564.Main.typeName())
        .addOptionsModification(TestingOptions::allowExperimentClassFileVersion)
        .run(parameters.getRuntime(), Jdk8272564.Main.typeName())
        .inspect(this::assertJdk8272564NotFixedCode)
        .assertSuccess();
  }
}
