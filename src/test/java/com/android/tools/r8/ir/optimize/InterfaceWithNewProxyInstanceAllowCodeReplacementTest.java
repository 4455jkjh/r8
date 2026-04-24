// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.lang.reflect.Proxy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InterfaceWithNewProxyInstanceAllowCodeReplacementTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(
            options -> {
              options.getTestingOptions().allowCodeReplacementInKeepRule = true;
              options.getTestingOptions().decoupleCodeReplacementFromOptimization = true;
            })
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class TestClass {

    public static void main(String[] args) {
      // With allowCodeReplacement=false, we will propagate to the call site that I.m
      // unconditionally throws and materialize a `throw null` instruction after the call.
      // The reflective identification sets allowCodeReplacement=true for I.m to avoid this.
      createProxyInstance().m();
      try {
        new A().m();
      } catch (RuntimeException e) {
        // Expected.
      }
    }

    static I createProxyInstance() {
      return (I)
          Proxy.newProxyInstance(
              TestClass.class.getClassLoader(),
              new Class<?>[] {I.class},
              (proxy, method, args1) -> {
                System.out.print("Hello");
                return null;
              });
    }
  }

  interface I {

    @NeverInline
    default void m() {
      System.out.println(", world!");
      throw new RuntimeException();
    }
  }

  @NeverClassInline
  static class A implements I {}
}
