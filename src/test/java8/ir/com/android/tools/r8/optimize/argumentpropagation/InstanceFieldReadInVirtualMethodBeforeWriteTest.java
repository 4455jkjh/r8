// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InstanceFieldReadInVirtualMethodBeforeWriteTest extends TestBase {

  private static final List<String> EXPECTED = ImmutableList.of("init", "hello");
  private static final List<String> UNEXPECTED = ImmutableList.of("hello");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testForJvm() throws Exception {
    parameters.assumeCfRuntime();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testForR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.isDexRuntime()
                && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.L),
            r -> r.assertSuccessWithOutputLines(UNEXPECTED),
            r -> r.assertSuccessWithOutputLines(EXPECTED));
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(A.INSTANCE.peek());
    }
  }

  @NeverClassInline
  static class A {

    static final A INSTANCE = new A("hello");

    // Intentionally not final to allow constructor inlining.
    private String f;

    A(String f) {
      String prev = peek();
      this.f = f;
      if (prev == null) {
        System.out.println("init");
      }
    }

    @NeverInline
    public String peek() {
      return this.f;
    }
  }
}
