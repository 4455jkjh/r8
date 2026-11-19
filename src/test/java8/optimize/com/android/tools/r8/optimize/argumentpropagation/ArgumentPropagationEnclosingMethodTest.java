// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ArgumentPropagationEnclosingMethodTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8Compat() throws Exception {
    testForR8Compat(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .addKeepMainRule(Main.class)
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!", "true");
  }

  static class Main {

    public static void main(String[] args) {
      Runnable runnable = createRunnable(42);
      runnable.run();
      System.out.println(runnable.getClass().getEnclosingMethod() != null);
    }

    @NeverInline
    static Runnable createRunnable(int constantArg) {
      if (constantArg != 42) {
        return null;
      }
      @NeverClassInline
      class LocalRunnable implements Runnable {

        @Override
        public void run() {
          System.out.println("Hello, world!");
        }
      }
      return new LocalRunnable();
    }
  }
}
