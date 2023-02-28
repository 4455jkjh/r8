// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.clinit;


import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InterfaceWithDefaultMethodInitializedByStaticGetOnSubClassTest
    extends ClassMayHaveInitializationSideEffectsTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InterfaceWithDefaultMethodInitializedByStaticGetOnSubClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .apply(
            runResult -> {
              if (parameters.isCfRuntime()
                  || parameters
                      .getApiLevel()
                      .isGreaterThanOrEqualTo(apiLevelWithStaticInterfaceMethodsSupport())) {
                runResult.assertSuccessWithOutputLines("I", "A");
              } else {
                // On older Android runtimes there is no default interface methods and therefore the
                // semantics is different.
                runResult.assertSuccessWithOutputLines("A");
              }
            });
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .allowStdoutMessages()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        // TODO(b/144266257): This should succeed with "I\nA" when default interface methods are
        //  supported, but we remove the default method I.m() because it is unused, which changes
        //  the behavior.
        .assertSuccessWithOutputLines("A");
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("I", "A");
  }

  @Test
  public void testClassInitializationMayHaveSideEffects() throws Exception {
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildInnerClasses(getClass())
                .addLibraryFile(ToolHelper.getMostRecentAndroidJar())
                .build(),
            TestClass.class);
    assertMayHaveClassInitializationSideEffects(appView, A.class);
  }

  static class TestClass {

    public static void main(String[] args) {
      Greeter greeter = A.aGreeter;
    }
  }

  interface I {

    Greeter iGreeter = new Greeter("I");

    default void m() {}
  }

  static class A implements I {

    static Greeter aGreeter = new Greeter("A");
  }

  static class Greeter {

    Greeter(String greeting) {
      System.out.println(greeting);
    }
  }
}
