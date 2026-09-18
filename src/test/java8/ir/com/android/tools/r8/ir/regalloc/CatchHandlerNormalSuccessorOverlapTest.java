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
public class CatchHandlerNormalSuccessorOverlapTest extends TestBase {

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
        .assertSuccessWithOutputLines("ok");
  }

  @Test
  public void testD8Debug() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addProgramClasses(TestClass.class)
        .debug()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("ok");
  }

  @Test
  public void testD8Release() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addProgramClasses(TestClass.class)
        .release()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("ok");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("ok");
  }

  public static class TestClass {
    static int[] staticArray = new int[] {42, 0};

    @NeverInline
    static String testCatchHandlerNormalSuccessorOverlap(int conditionValue, int[] targetArray) {
      try {
        if (conditionValue > 0) {
          targetArray[0] = 1;
        } else {
          targetArray[0] = 2;
          return "early";
        }
        targetArray[1] = 3;
      } catch (RuntimeException ignoredException) {
        // Empty catch handler merges directly with normal control flow after try-catch.
      } catch (Error errorException) {
        return "error: " + errorException.getMessage();
      }
      return "ok";
    }

    public static void main(String[] args) {
      System.out.println(testCatchHandlerNormalSuccessorOverlap(1, staticArray));
    }
  }
}
