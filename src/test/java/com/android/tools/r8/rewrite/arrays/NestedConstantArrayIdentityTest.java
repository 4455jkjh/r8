// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.rewrite.arrays;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.internal.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// Regression test for recreating a nested constant array at its array put: the identity of an
// inner array that is stored twice, or defined outside a loop, must be preserved.
@RunWith(Parameterized.class)
public class NestedConstantArrayIdentityTest extends TestBase {

  private static final String EXPECTED_OUTPUT = StringUtils.lines("99", "true", "99", "true");

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
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addInnerClasses(getClass())
        .release()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  public static class TestClass {

    @NeverInline
    static int[][] sharedRow() {
      int[] row = {1, 2, 3, 4, 5, 6};
      return new int[][] {row, row};
    }

    @NeverInline
    static Object[] rowOutsideLoop() {
      int[] row = {1, 2, 3, 4, 5, 6};
      Object[] result = new Object[2];
      for (int i = 0; i < 2; i++) {
        int[][] matrix = {row};
        result[i] = matrix[0];
      }
      return result;
    }

    @NeverInline
    static String[][] sharedStringRow() {
      String[] row = {"a", "b", "c", "d", "e", "f"};
      return new String[][] {row, row};
    }

    public static void main(String[] args) {
      int[][] shared = sharedRow();
      shared[0][0] = 99;
      System.out.println(shared[1][0]);
      Object[] rows = rowOutsideLoop();
      System.out.println(rows[0] == rows[1]);
      String[][] sharedStrings = sharedStringRow();
      sharedStrings[0][0] = "99";
      System.out.println(sharedStrings[1][0]);
      System.out.println(sharedStrings[0] == sharedStrings[1]);
    }
  }
}
