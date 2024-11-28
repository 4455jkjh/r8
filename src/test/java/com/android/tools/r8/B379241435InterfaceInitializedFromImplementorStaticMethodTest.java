// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// Regression test for a variant of b/379241435.
@RunWith(Parameterized.class)
public class B379241435InterfaceInitializedFromImplementorStaticMethodTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("B.B()", "A.A()", "I.f()");

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            parameters.isDexRuntime()
                && parameters
                    .getApiLevel()
                    .isLessThan(apiLevelWithDefaultInterfaceMethodsSupport()),
            r -> r.assertSuccessWithOutput(EXPECTED_OUTPUT),
            r -> r.assertSuccessWithOutputLines("B.B()", "I.f()"));
  }

  public static class TestClass {
    public static void main(String[] args) {
      // Instantiating B does not trigger class initialization of I.
      B b = new B();
      // Invoking m indirectly trigger class initialization of I as it calls a static method on I.
      B.m();
    }
  }

  interface I {
    A a = new A();

    static void f() {
      System.out.println("I.f()");
    }
  }

  static class A {
    A() {
      System.out.println("A.A()");
    }
  }

  @NeverClassInline
  static class B implements I {
    B() {
      System.out.println("B.B()");
    }

    @NeverInline
    static void m() {
      I.f();
    }
  }
}
