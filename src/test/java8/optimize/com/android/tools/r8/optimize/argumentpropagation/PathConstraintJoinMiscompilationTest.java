// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation;

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
public class PathConstraintJoinMiscompilationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  // Reproduction of b/557266247.

  @Test
  public void testForJvm() throws Exception {
    parameters.assumeCfRuntime();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("!= 99", "== 99");
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("!= 99", "== 99");
  }

  public static class Main {

    public static void main(String[] args) {
      forward(0, 5, 0);
      forward(1, 5, 0);
    }

    @SuppressWarnings("SameParameterValue")
    @NeverInline
    static void forward(int outer, int inner, int x) {
      int v;
      // When outer == 0, execution short-circuits to the else block without evaluating inner.
      // Therefore, the else block cannot assume inner == 0.
      if (outer != 0 && inner != 0) {
        v = 99;
      } else {
        v = x;
      }
      sink(v);
    }

    @NeverInline
    static void sink(int p) {
      if (p != 99) {
        System.out.println("!= 99");
        return;
      }
      System.out.println("== 99");
    }
  }
}
