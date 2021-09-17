// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.lambdas;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LambdaWithPrivateInterfaceInvokeTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello world");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public LambdaWithPrivateInterfaceInvokeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(TestClass.class, MyFun.class, A.class)
        .addProgramClassFileData(getTransformForI())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, MyFun.class, A.class)
        .addProgramClassFileData(getTransformForI())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  private byte[] getTransformForI() throws Exception {
    return transformer(I.class).setPrivate(I.class.getDeclaredMethod("bar")).transform();
  }

  interface I {
    /* private */ default String bar() {
      return "Hello world";
    }

    default void foo() {
      TestClass.run(
          () -> {
            System.out.println(bar());
          });
    }
  }

  interface MyFun {
    void run();
  }

  static class A implements I {}

  static class TestClass {

    public static void run(MyFun fn) {
      fn.run();
    }

    public static void main(String[] args) {
      new A().foo();
    }
  }
}
