// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.finalizer;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for b/573818534: the branch diamond inverter must not introduce a goto. */
@RunWith(Parameterized.class)
public class BranchDiamondInverterDebugGotoTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForD8(parameters)
        .addInnerClasses(getClass())
        .setMode(CompilationMode.DEBUG)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject method =
                  inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("squareToLen");
              assertThat(method, isPresent());
              assertEquals(
                  0, method.streamInstructions().filter(InstructionSubject::isGoto).count());
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("4", "4", "8");
  }

  static class TestClass {

    static int[] squareToLen(int[] x, int len, int[] z) {
      int zlen = len << 1;
      if (z == null || z.length < zlen) {
        z = new int[zlen];
      }
      return consume(x, len, z, zlen);
    }

    static int[] consume(int[] x, int len, int[] z, int zlen) {
      return z;
    }

    public static void main(String[] args) {
      System.out.println(squareToLen(null, 2, null).length);
      System.out.println(squareToLen(null, 2, new int[1]).length);
      System.out.println(squareToLen(null, 2, new int[8]).length);
    }
  }
}
