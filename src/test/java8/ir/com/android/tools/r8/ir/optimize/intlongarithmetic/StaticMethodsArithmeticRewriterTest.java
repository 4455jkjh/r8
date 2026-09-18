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
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.internal.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StaticMethodsArithmeticRewriterTest extends TestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "42", "42", "42", "42", "42", "42", "42", "42", "42", "42", "42", "42", "0", "0", "42",
          "42", "0", "0", "42", "42", "42", "42", "5", "10", "4", "8", "0", "0", "0", "0", "0",
          "0");

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
      if (!parameters.canUseJavaLangDivideUnsigned()
          && (method.getOriginalMethodName().contains("Unsigned")
              || method.getOriginalMethodName().contains("floor")
              || method.getOriginalMethodName().contains("Exact"))) {
        continue;
      }
      assertEquals(
          0,
          method
              .streamInstructions()
              .filter(
                  i -> i.isInvokeStatic() || i.isIntArithmeticBinop() || i.isLongArithmeticBinop())
              .count());
      if (method.getOriginalMethodName().contains("PowerOfTwo")) {
        assertEquals(
            1,
            method.streamInstructions().filter(InstructionSubject::isUnsignedShiftRight).count());
      }
    }
  }

  public static class Main {

    public static void main(String[] args) {
      sumIntLeft(42);
      sumIntRight(42);
      sumLongLeft(42L);
      sumLongRight(42L);

      addExactIntLeft(42);
      addExactIntRight(42);
      addExactLongLeft(42L);
      addExactLongRight(42L);

      subtractExactIntRight(42);
      subtractExactLongRight(42L);

      multiplyExactIntLeftIdentity(42);
      multiplyExactIntRightIdentity(42);
      multiplyExactIntLeftAbsorbing(42);
      multiplyExactIntRightAbsorbing(42);

      multiplyExactLongLeftIdentity(42L);
      multiplyExactLongRightIdentity(42L);
      multiplyExactLongLeftAbsorbing(42L);
      multiplyExactLongRightAbsorbing(42L);

      floorDivIntRight(42);
      floorDivLongRight(42L);

      divideUnsignedIntRight(42);
      divideUnsignedLongRight(42L);
      divideUnsignedIntPowerOfTwo(40);
      divideUnsignedIntPowerOfTwo(80);
      divideUnsignedLongPowerOfTwo(64L);
      divideUnsignedLongPowerOfTwo(128L);

      remainderUnsignedInt1(42);
      remainderUnsignedLong1(42L);

      floorModInt1(42);
      floorModIntM1(42);
      floorModLong1(42L);
      floorModLongM1(42L);
    }

    @NeverInline
    public static void sumIntLeft(int x) {
      System.out.println(Integer.sum(0, x));
    }

    @NeverInline
    public static void sumIntRight(int x) {
      System.out.println(Integer.sum(x, 0));
    }

    @NeverInline
    public static void sumLongLeft(long x) {
      System.out.println(Long.sum(0L, x));
    }

    @NeverInline
    public static void sumLongRight(long x) {
      System.out.println(Long.sum(x, 0L));
    }

    @NeverInline
    public static void addExactIntLeft(int x) {
      System.out.println(Math.addExact(0, x));
    }

    @NeverInline
    public static void addExactIntRight(int x) {
      System.out.println(Math.addExact(x, 0));
    }

    @NeverInline
    public static void addExactLongLeft(long x) {
      System.out.println(Math.addExact(0L, x));
    }

    @NeverInline
    public static void addExactLongRight(long x) {
      System.out.println(Math.addExact(x, 0L));
    }

    @NeverInline
    public static void subtractExactIntRight(int x) {
      System.out.println(Math.subtractExact(x, 0));
    }

    @NeverInline
    public static void subtractExactLongRight(long x) {
      System.out.println(Math.subtractExact(x, 0L));
    }

    @NeverInline
    public static void multiplyExactIntLeftIdentity(int x) {
      System.out.println(Math.multiplyExact(1, x));
    }

    @NeverInline
    public static void multiplyExactIntRightIdentity(int x) {
      System.out.println(Math.multiplyExact(x, 1));
    }

    @NeverInline
    public static void multiplyExactIntLeftAbsorbing(int x) {
      System.out.println(Math.multiplyExact(0, x));
    }

    @NeverInline
    public static void multiplyExactIntRightAbsorbing(int x) {
      System.out.println(Math.multiplyExact(x, 0));
    }

    @NeverInline
    public static void multiplyExactLongLeftIdentity(long x) {
      System.out.println(Math.multiplyExact(1L, x));
    }

    @NeverInline
    public static void multiplyExactLongRightIdentity(long x) {
      System.out.println(Math.multiplyExact(x, 1L));
    }

    @NeverInline
    public static void multiplyExactLongLeftAbsorbing(long x) {
      System.out.println(Math.multiplyExact(0L, x));
    }

    @NeverInline
    public static void multiplyExactLongRightAbsorbing(long x) {
      System.out.println(Math.multiplyExact(x, 0L));
    }

    @NeverInline
    public static void floorDivIntRight(int x) {
      System.out.println(Math.floorDiv(x, 1));
    }

    @NeverInline
    public static void floorDivLongRight(long x) {
      System.out.println(Math.floorDiv(x, 1L));
    }

    @NeverInline
    public static void divideUnsignedIntRight(int x) {
      System.out.println(Integer.divideUnsigned(x, 1));
    }

    @NeverInline
    public static void divideUnsignedLongRight(long x) {
      System.out.println(Long.divideUnsigned(x, 1L));
    }

    @NeverInline
    public static void divideUnsignedIntPowerOfTwo(int x) {
      System.out.println(Integer.divideUnsigned(x, 8));
    }

    @NeverInline
    public static void divideUnsignedLongPowerOfTwo(long x) {
      System.out.println(Long.divideUnsigned(x, 16L));
    }

    @NeverInline
    public static void remainderUnsignedInt1(int x) {
      System.out.println(Integer.remainderUnsigned(x, 1));
    }

    @NeverInline
    public static void remainderUnsignedLong1(long x) {
      System.out.println(Long.remainderUnsigned(x, 1L));
    }

    @NeverInline
    public static void floorModInt1(int x) {
      System.out.println(Math.floorMod(x, 1));
    }

    @NeverInline
    public static void floorModIntM1(int x) {
      System.out.println(Math.floorMod(x, -1));
    }

    @NeverInline
    public static void floorModLong1(long x) {
      System.out.println(Math.floorMod(x, 1L));
    }

    @NeverInline
    public static void floorModLongM1(long x) {
      System.out.println(Math.floorMod(x, -1L));
    }
  }
}
