// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.arrays;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FilledNewArrayStaticGetSideEffectOrderTest extends TestBase {

  private static final List<String> EXPECETD =
      ImmutableList.of("Holder.<clinit>", "check: true", "42", "100");
  private static final List<String> UNEXPECETD =
      ImmutableList.of("check: false", "Holder.<clinit>", "-1", "100");

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
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECETD);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/557264673): Should be EXPECTED.
        .assertSuccessWithOutputLines(UNEXPECETD);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/557264673): Should be EXPECTED.
        .assertSuccessWithOutputLines(UNEXPECETD);
  }

  static class State {
    static boolean initialized = false;
  }

  static class Holder {
    static {
      System.out.println("Holder.<clinit>");
      State.initialized = true;
    }

    static long F = 42L;
  }

  static class Main {

    @NeverInline
    static long expr() {
      System.out.println("check: " + State.initialized);
      Holder.F = -1L;
      return 100L;
    }

    public static void main(String[] args) {
      long[] arr = new long[] {Holder.F, expr()};
      System.out.println(arr[0]);
      System.out.println(arr[1]);
    }
  }
}
