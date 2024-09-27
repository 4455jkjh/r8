// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B369739224Test extends TestBase {

  private final String EXPECTED_OUTPUT =
      StringUtils.lines("Caught!", "Caught!", "Caught!", "Caught!");

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
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static class TestClass {

    public static void main(String[] args) {
      String s = "";
      char[] a = new char[0];
      int c = '.';
      try {
        new StringBuilder().append(s, 0, c);
      } catch (IndexOutOfBoundsException e) {
        System.out.println("Caught!");
      }
      try {
        new StringBuffer().append(s, 0, c);
      } catch (IndexOutOfBoundsException e) {
        System.out.println("Caught!");
      }
      try {
        new StringBuilder().append(a, 0, c);
      } catch (IndexOutOfBoundsException e) {
        System.out.println("Caught!");
      }
      try {
        new StringBuffer().append(a, 0, c);
      } catch (IndexOutOfBoundsException e) {
        System.out.println("Caught!");
      }
    }
  }
}
