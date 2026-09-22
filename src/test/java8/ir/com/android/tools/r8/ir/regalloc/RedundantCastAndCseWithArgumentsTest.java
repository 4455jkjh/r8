// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.regalloc;

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
public class RedundantCastAndCseWithArgumentsTest extends TestBase {

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
        .addProgramClasses(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("hello:5:1234");
  }

  @Test
  public void testD8Debug() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addProgramClasses(TestClass.class)
        .debug()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("hello:5:1234");
  }

  @Test
  public void testD8Release() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addProgramClasses(TestClass.class)
        .release()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("hello:5:1234");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("hello:5:1234");
  }

  public static class TestClass {
    @NeverInline
    static void sink(Object inputObject) {
      if (inputObject == null) {
        throw new RuntimeException("null");
      }
    }

    @NeverInline
    static String testRedundantCastAndCseWithArguments(
        int firstParam, int secondParam, int thirdParam, int fourthParam, Object inputObject) {
      String firstCastString = (String) inputObject;
      String secondCastString = (String) inputObject;
      sink(inputObject);
      return firstCastString
          + ":"
          + secondCastString.length()
          + ":"
          + firstParam
          + secondParam
          + thirdParam
          + fourthParam;
    }

    public static void main(String[] args) {
      System.out.println(testRedundantCastAndCseWithArguments(1, 2, 3, 4, "hello"));
    }
  }
}
