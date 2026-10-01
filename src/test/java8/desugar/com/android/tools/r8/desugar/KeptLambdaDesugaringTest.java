// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for b/120971047. */
@RunWith(Parameterized.class)
public class KeptLambdaDesugaringTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(Backend.DEX)
        .addInnerClasses(KeptLambdaDesugaringTest.class)
        .addKeepRules("-keep class ** { *; }")
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput("Hello world");
  }

  static class TestClass {

    public static void main(String[] args) {
      new A("Hello world").method();
    }
  }

  static class A {

    private final String message;

    public A(String message) {
      this.message = message;
    }

    public void method() {
      Runnable runnable = () -> {
        // The use of reflection will trigger Enqueuer.handleReflectiveBehavior.
        try {
          A.class.getDeclaredField("message");
        } catch (NoSuchFieldException e) {
          throw new RuntimeException(e);
        }
        System.out.print(message);
      };
      runnable.run();
    }
  }
}
