// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion.finalizer;

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
public class BasicBlockReordererTryCatchThrowRegressionTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("OK");
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("OK");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("OK");
  }

  static class TestClass {

    static int addExact(int x, int y) {
      int r = x + y;
      if (((x ^ r) & (y ^ r)) < 0) {
        throw new ArithmeticException("integer overflow");
      }
      return r;
    }

    static int multiplyExact(int x, int y) {
      long r = (long) x * (long) y;
      if ((int) r != r) {
        throw new ArithmeticException("integer overflow");
      }
      return (int) r;
    }

    @NeverInline
    static void fail(String msg) {
      throw new RuntimeException(msg);
    }

    @NeverInline
    static void testExact(int x, int y) {
      ArithmeticException caughtAdd = null;
      try {
        int sum = addExact(x, y);
        if ((long) sum != (long) x + (long) y) {
          fail("sum mismatch");
        }
      } catch (ArithmeticException e) {
        caughtAdd = e;
      }
      if (((long) x + (long) y != (int) ((long) x + (long) y)) != (caughtAdd != null)) {
        fail("addExact exception mismatch");
      }

      try {
        int product = multiplyExact(x, y);
        if ((long) product != (long) x * (long) y) {
          fail("product mismatch");
        }
      } catch (ArithmeticException e) {
        if ((long) x * (long) y == (int) ((long) x * (long) y)) {
          fail("unexpected multiplyExact overflow");
        }
      }
    }

    public static void main(String[] args) {
      testExact(0, 0);
      testExact(1, 1);
      testExact(Integer.MAX_VALUE, 1);
      testExact(Integer.MIN_VALUE, -1);
      testExact(Integer.MAX_VALUE, 2);
      System.out.println("OK");
    }
  }
}
