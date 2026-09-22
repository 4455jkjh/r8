// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.intlongarithmetic;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.internal.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StrengthReductionArithmeticRewriterTest extends TestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "2688", "-64", "2688", "-64", "2688", "-64", "2688", "-64", "84", "-2", "84", "-2");

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters)
        .addProgramClasses(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(Main.class);
    for (FoundMethodSubject method :
        clazz.allMethods(m -> !m.getOriginalMethodName().contains("main"))) {
      assertEquals(
          0,
          method
              .streamInstructions()
              .filter(i -> i.isIntArithmeticBinop() || i.isLongArithmeticBinop())
              .count());
      assertEquals(
          1,
          method
              .streamInstructions()
              .filter(i -> i.isIntLogicalBinop() || i.isLongLogicalBinop())
              .count());
    }
  }

  public static class Main {

    public static void main(String[] args) {
      mulI(42);
      mulI(Integer.MAX_VALUE);
      mulL(42L);
      mulL(Long.MAX_VALUE);

      mulI2(42);
      mulI2(Integer.MAX_VALUE);
      mulL2(42L);
      mulL2(Long.MAX_VALUE);

      addI(42);
      addI(Integer.MAX_VALUE);
      addL(42L);
      addL(Long.MAX_VALUE);
    }

    @NeverInline
    public static void mulI(int x) {
      System.out.println(x * 64);
    }

    @NeverInline
    public static void mulL(long x) {
      System.out.println(x * 64);
    }

    @NeverInline
    public static void mulI2(int x) {
      System.out.println(64 * x);
    }

    @NeverInline
    public static void mulL2(long x) {
      System.out.println(64 * x);
    }

    @NeverInline
    public static void addI(int x) {
      System.out.println(x + x);
    }

    @NeverInline
    public static void addL(long x) {
      System.out.println(x + x);
    }
  }
}
