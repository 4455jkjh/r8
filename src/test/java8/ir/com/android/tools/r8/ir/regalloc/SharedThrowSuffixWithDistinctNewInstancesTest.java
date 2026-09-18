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
public class SharedThrowSuffixWithDistinctNewInstancesTest extends TestBase {

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
        .assertSuccessWithOutputLines("caught: A10");
  }

  @Test
  public void testD8Debug() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addProgramClasses(TestClass.class)
        .debug()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("caught: A10");
  }

  @Test
  public void testD8Release() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addProgramClasses(TestClass.class)
        .release()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("caught: A10");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("caught: A10");
  }

  public static class TestClass {
    static int throwFormattedError(String prefixMessage, int errorValue) {
      throw new IllegalArgumentException(
          new StringBuilder(prefixMessage).append(errorValue).toString());
    }

    @NeverInline
    static int testSharedThrowSuffix(int modeSelector, int errorValue) {
      return modeSelector == 1
          ? throwFormattedError("A", errorValue)
          : throwFormattedError("B", errorValue);
    }

    public static void main(String[] args) {
      try {
        testSharedThrowSuffix(1, 10);
      } catch (IllegalArgumentException caughtException) {
        System.out.println("caught: " + caughtException.getMessage());
      }
    }
  }
}
