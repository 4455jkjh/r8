// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// Reproduction of b/531533396.
@RunWith(Parameterized.class)
public class StringBuilderConstructionFromPhiFirstTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addInnerClasses(getClass())
        .release()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("cache_Hello 1 time(s)!", "Hello 2 time(s)!");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("cache_Hello 1 time(s)!", "Hello 2 time(s)!");
  }

  static class Main {

    public static void main(String[] args) {
      test(1);
      test(2);
    }

    @NeverInline
    public static Object getStringBuilder(int i) {
      if (i == 1) {
        return new StringBuilder("cache_");
      }
      return null;
    }

    @NeverInline
    private static void test(int i) {
      Object obj;
      if (i == 1) {
        obj = getStringBuilder(i);
      } else {
        obj = new StringBuilder(256);
      }
      StringBuilder sb = (StringBuilder) obj;
      sb.append("Hello ");
      sb.append(i);
      sb.append(" time(s)!");
      System.out.println(sb);
    }
  }
}
