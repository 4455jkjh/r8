// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.intlongarithmetic;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IntLongArithmeticRegressionTest extends TestBase {

  private static String expectedPairsAndSuccessiveOutput;
  private static String expectedUnusedThrowingStaticOutput;
  private static String expectedMinMaxPhiOutput;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @BeforeClass
  public static void computeExpectedJvmOutputs() throws Exception {
    expectedPairsAndSuccessiveOutput =
        testForJvm(getStaticTemp())
            .addTestClasspath()
            .run(TestRuntime.getCheckedInJdk11(), PairsAndSuccessiveMain.class)
            .assertSuccess()
            .getStdOut();

    expectedUnusedThrowingStaticOutput =
        testForJvm(getStaticTemp())
            .addTestClasspath()
            .run(TestRuntime.getCheckedInJdk11(), UnusedThrowingStaticMain.class)
            .assertSuccess()
            .getStdOut();

    expectedMinMaxPhiOutput =
        testForJvm(getStaticTemp())
            .addTestClasspath()
            .run(TestRuntime.getCheckedInJdk11(), MinMaxPhiMain.class)
            .assertSuccess()
            .getStdOut();
  }

  @Test
  public void testPairsAndSuccessiveD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addProgramClasses(PairsAndSuccessiveMain.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), PairsAndSuccessiveMain.class)
        .assertSuccessWithOutput(expectedPairsAndSuccessiveOutput);
  }

  @Test
  public void testPairsAndSuccessiveR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(PairsAndSuccessiveMain.class)
        .addKeepMainRule(PairsAndSuccessiveMain.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), PairsAndSuccessiveMain.class)
        .assertSuccessWithOutput(expectedPairsAndSuccessiveOutput);
  }

  @Test
  public void testUnusedThrowingStaticD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addProgramClasses(UnusedThrowingStaticMain.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), UnusedThrowingStaticMain.class)
        .assertSuccessWithOutput(expectedUnusedThrowingStaticOutput);
  }

  @Test
  public void testUnusedThrowingStaticR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(UnusedThrowingStaticMain.class)
        .addKeepMainRule(UnusedThrowingStaticMain.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), UnusedThrowingStaticMain.class)
        .assertSuccessWithOutput(expectedUnusedThrowingStaticOutput);
  }

  @Test
  public void testMinMaxPhiD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addProgramClasses(MinMaxPhiMain.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MinMaxPhiMain.class)
        .assertSuccessWithOutput(expectedMinMaxPhiOutput);
  }

  @Test
  public void testMinMaxPhiR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(MinMaxPhiMain.class)
        .addKeepMainRule(MinMaxPhiMain.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MinMaxPhiMain.class)
        .assertSuccessWithOutput(expectedMinMaxPhiOutput);
  }

  public static class PairsAndSuccessiveMain {

    static final int[] INT_VALUES = {Integer.MIN_VALUE, -10, -1, 0, 1, 2, 10, Integer.MAX_VALUE};

    static final long[] LONG_VALUES = {Long.MIN_VALUE, -10L, -1L, 0L, 1L, 2L, 10L, Long.MAX_VALUE};

    @NeverInline
    static int disguise(int v) {
      return v;
    }

    @NeverInline
    static long disguise(long v) {
      return v;
    }

    static void checkInt(int v) {
      System.out.println(v);
    }

    static void checkLong(long v) {
      System.out.println(v);
    }

    static void checkEx() {
      System.out.println("EX");
    }

    public static void main(String[] args) {
      for (int a : INT_VALUES) {
        testIntBinopsAndStatics(a);
        for (int b : INT_VALUES) {
          testIntSuccessive(a, b);
        }
      }
      for (long a : LONG_VALUES) {
        testLongBinopsAndStatics(a);
        for (long b : LONG_VALUES) {
          testLongSuccessive(a, b);
        }
      }
    }

    @NeverInline
    static void testIntBinopsAndStatics(int x) {
      int v = disguise(x);
      // 1. Add
      checkInt(v + Integer.MIN_VALUE);
      checkInt(v + -10);
      checkInt(v + -1);
      checkInt(v + 0);
      checkInt(v + 1);
      checkInt(v + 2);
      checkInt(v + 10);
      checkInt(v + Integer.MAX_VALUE);
      checkInt(Integer.MIN_VALUE + v);
      checkInt(-10 + v);
      checkInt(-1 + v);
      checkInt(0 + v);
      checkInt(1 + v);
      checkInt(2 + v);
      checkInt(10 + v);
      checkInt(Integer.MAX_VALUE + v);

      // 2. Sub
      checkInt(v - Integer.MIN_VALUE);
      checkInt(v - -10);
      checkInt(v - -1);
      checkInt(v - 0);
      checkInt(v - 1);
      checkInt(v - 2);
      checkInt(v - 10);
      checkInt(v - Integer.MAX_VALUE);
      checkInt(Integer.MIN_VALUE - v);
      checkInt(-10 - v);
      checkInt(-1 - v);
      checkInt(0 - v);
      checkInt(1 - v);
      checkInt(2 - v);
      checkInt(10 - v);
      checkInt(Integer.MAX_VALUE - v);

      // 3. Mul
      checkInt(v * Integer.MIN_VALUE);
      checkInt(v * -10);
      checkInt(v * -1);
      checkInt(v * 0);
      checkInt(v * 1);
      checkInt(v * 2);
      checkInt(v * 10);
      checkInt(v * Integer.MAX_VALUE);
      checkInt(Integer.MIN_VALUE * v);
      checkInt(-10 * v);
      checkInt(-1 * v);
      checkInt(0 * v);
      checkInt(1 * v);
      checkInt(2 * v);
      checkInt(10 * v);
      checkInt(Integer.MAX_VALUE * v);

      // 4. Div & Rem
      checkInt(v / Integer.MIN_VALUE);
      checkInt(v / -10);
      checkInt(v / -1);
      try {
        checkInt(v / 0);
      } catch (ArithmeticException e) {
        checkEx();
      }
      checkInt(v / 1);
      checkInt(v / 2);
      checkInt(v / 10);
      checkInt(v / Integer.MAX_VALUE);

      checkInt(v % Integer.MIN_VALUE);
      checkInt(v % -10);
      checkInt(v % -1);
      try {
        checkInt(v % 0);
      } catch (ArithmeticException e) {
        checkEx();
      }
      checkInt(v % 1);
      checkInt(v % 2);
      checkInt(v % 10);
      checkInt(v % Integer.MAX_VALUE);

      if (v != 0) {
        checkInt(Integer.MIN_VALUE / v);
        checkInt(-10 / v);
        checkInt(-1 / v);
        checkInt(0 / v);
        checkInt(1 / v);
        checkInt(2 / v);
        checkInt(10 / v);
        checkInt(Integer.MAX_VALUE / v);

        checkInt(Integer.MIN_VALUE % v);
        checkInt(-10 % v);
        checkInt(-1 % v);
        checkInt(0 % v);
        checkInt(1 % v);
        checkInt(2 % v);
        checkInt(10 % v);
        checkInt(Integer.MAX_VALUE % v);
      }

      // 5. Bitwise And, Or, Xor
      checkInt(v & Integer.MIN_VALUE);
      checkInt(v & -10);
      checkInt(v & -1);
      checkInt(v & 0);
      checkInt(v & 1);
      checkInt(v & 2);
      checkInt(v & 10);
      checkInt(v & Integer.MAX_VALUE);

      checkInt(v | Integer.MIN_VALUE);
      checkInt(v | -10);
      checkInt(v | -1);
      checkInt(v | 0);
      checkInt(v | 1);
      checkInt(v | 2);
      checkInt(v | 10);
      checkInt(v | Integer.MAX_VALUE);

      checkInt(v ^ Integer.MIN_VALUE);
      checkInt(v ^ -10);
      checkInt(v ^ -1);
      checkInt(v ^ 0);
      checkInt(v ^ 1);
      checkInt(v ^ 2);
      checkInt(v ^ 10);
      checkInt(v ^ Integer.MAX_VALUE);

      // 6. Shifts: Shl, Shr, Ushr
      checkInt(v << Integer.MIN_VALUE);
      checkInt(v << -10);
      checkInt(v << -1);
      checkInt(v << 0);
      checkInt(v << 1);
      checkInt(v << 2);
      checkInt(v << 10);
      checkInt(v << Integer.MAX_VALUE);
      checkInt(0 << v);
      checkInt(1 << v);
      checkInt(-1 << v);

      checkInt(v >> Integer.MIN_VALUE);
      checkInt(v >> -10);
      checkInt(v >> -1);
      checkInt(v >> 0);
      checkInt(v >> 1);
      checkInt(v >> 2);
      checkInt(v >> 10);
      checkInt(v >> Integer.MAX_VALUE);
      checkInt(0 >> v);
      checkInt(-1 >> v);

      checkInt(v >>> Integer.MIN_VALUE);
      checkInt(v >>> -10);
      checkInt(v >>> -1);
      checkInt(v >>> 0);
      checkInt(v >>> 1);
      checkInt(v >>> 2);
      checkInt(v >>> 10);
      checkInt(v >>> Integer.MAX_VALUE);
      checkInt(0 >>> v);
      checkInt(-1 >>> v);

      // 7. Static methods: divideUnsigned, remainderUnsigned
      checkInt(Integer.divideUnsigned(v, Integer.MIN_VALUE));
      checkInt(Integer.divideUnsigned(v, -10));
      checkInt(Integer.divideUnsigned(v, -1));
      try {
        checkInt(Integer.divideUnsigned(v, 0));
      } catch (ArithmeticException e) {
        checkEx();
      }
      checkInt(Integer.divideUnsigned(v, 1));
      checkInt(Integer.divideUnsigned(v, 2));
      checkInt(Integer.divideUnsigned(v, 10));
      checkInt(Integer.divideUnsigned(v, Integer.MAX_VALUE));
      if (v != 0) {
        checkInt(Integer.divideUnsigned(0, v));
        checkInt(Integer.divideUnsigned(1, v));
        checkInt(Integer.divideUnsigned(-1, v));
      }

      checkInt(Integer.remainderUnsigned(v, Integer.MIN_VALUE));
      checkInt(Integer.remainderUnsigned(v, -10));
      checkInt(Integer.remainderUnsigned(v, -1));
      try {
        checkInt(Integer.remainderUnsigned(v, 0));
      } catch (ArithmeticException e) {
        checkEx();
      }
      checkInt(Integer.remainderUnsigned(v, 1));
      checkInt(Integer.remainderUnsigned(v, 2));
      checkInt(Integer.remainderUnsigned(v, 10));
      checkInt(Integer.remainderUnsigned(v, Integer.MAX_VALUE));
      if (v != 0) {
        checkInt(Integer.remainderUnsigned(0, v));
        checkInt(Integer.remainderUnsigned(1, v));
        checkInt(Integer.remainderUnsigned(-1, v));
      }

      // 8. Static methods: Integer.min, Integer.max, Math.min, Math.max, Integer.sum
      checkInt(Integer.min(v, Integer.MIN_VALUE));
      checkInt(Integer.min(v, -10));
      checkInt(Integer.min(v, -1));
      checkInt(Integer.min(v, 0));
      checkInt(Integer.min(v, 1));
      checkInt(Integer.min(v, 2));
      checkInt(Integer.min(v, 10));
      checkInt(Integer.min(v, Integer.MAX_VALUE));

      checkInt(Integer.max(v, Integer.MIN_VALUE));
      checkInt(Integer.max(v, -10));
      checkInt(Integer.max(v, -1));
      checkInt(Integer.max(v, 0));
      checkInt(Integer.max(v, 1));
      checkInt(Integer.max(v, 2));
      checkInt(Integer.max(v, 10));
      checkInt(Integer.max(v, Integer.MAX_VALUE));

      checkInt(Math.min(v, Integer.MIN_VALUE));
      checkInt(Math.min(v, -10));
      checkInt(Math.min(v, -1));
      checkInt(Math.min(v, 0));
      checkInt(Math.min(v, 1));
      checkInt(Math.min(v, 2));
      checkInt(Math.min(v, 10));
      checkInt(Math.min(v, Integer.MAX_VALUE));

      checkInt(Math.max(v, Integer.MIN_VALUE));
      checkInt(Math.max(v, -10));
      checkInt(Math.max(v, -1));
      checkInt(Math.max(v, 0));
      checkInt(Math.max(v, 1));
      checkInt(Math.max(v, 2));
      checkInt(Math.max(v, 10));
      checkInt(Math.max(v, Integer.MAX_VALUE));

      checkInt(Integer.sum(v, Integer.MIN_VALUE));
      checkInt(Integer.sum(v, -10));
      checkInt(Integer.sum(v, -1));
      checkInt(Integer.sum(v, 0));
      checkInt(Integer.sum(v, 1));
      checkInt(Integer.sum(v, 2));
      checkInt(Integer.sum(v, 10));
      checkInt(Integer.sum(v, Integer.MAX_VALUE));

      // 9. Static methods: Math.addExact, Math.subtractExact, Math.multiplyExact
      for (int c : INT_VALUES) {
        try {
          checkInt(Math.addExact(v, c));
        } catch (ArithmeticException e) {
          checkEx();
        }
        try {
          checkInt(Math.subtractExact(v, c));
        } catch (ArithmeticException e) {
          checkEx();
        }
        try {
          checkInt(Math.multiplyExact(v, c));
        } catch (ArithmeticException e) {
          checkEx();
        }
      }
      checkInt(Math.addExact(v, 0));
      checkInt(Math.addExact(0, v));
      checkInt(Math.subtractExact(v, 0));
      checkInt(Math.multiplyExact(v, 0));
      checkInt(Math.multiplyExact(0, v));
      checkInt(Math.multiplyExact(v, 1));
      checkInt(Math.multiplyExact(1, v));

      // 10. Static methods: Math.floorDiv, Math.floorMod
      checkInt(Math.floorDiv(v, Integer.MIN_VALUE));
      checkInt(Math.floorDiv(v, -10));
      checkInt(Math.floorDiv(v, -1));
      try {
        checkInt(Math.floorDiv(v, 0));
      } catch (ArithmeticException e) {
        checkEx();
      }
      checkInt(Math.floorDiv(v, 1));
      checkInt(Math.floorDiv(v, 2));
      checkInt(Math.floorDiv(v, 10));
      checkInt(Math.floorDiv(v, Integer.MAX_VALUE));
      if (v != 0) {
        checkInt(Math.floorDiv(0, v));
      }

      checkInt(Math.floorMod(v, Integer.MIN_VALUE));
      checkInt(Math.floorMod(v, -10));
      checkInt(Math.floorMod(v, -1));
      try {
        checkInt(Math.floorMod(v, 0));
      } catch (ArithmeticException e) {
        checkEx();
      }
      checkInt(Math.floorMod(v, 1));
      checkInt(Math.floorMod(v, 2));
      checkInt(Math.floorMod(v, 10));
      checkInt(Math.floorMod(v, Integer.MAX_VALUE));
      if (v != 0) {
        checkInt(Math.floorMod(0, v));
      }
    }

    @NeverInline
    static void testIntSuccessive(int x, int y) {
      int v = disguise(x);
      // Add / Sub chains with constants from INT_VALUES
      checkInt((v + 10) + -10);
      checkInt((v + Integer.MAX_VALUE) + 1);
      checkInt((v + Integer.MIN_VALUE) + -1);
      checkInt((v + 10) - 10);
      checkInt((v - 10) + 10);
      checkInt((v - 10) - -10);
      checkInt((10 - v) + 2);
      checkInt((10 - v) - 2);
      checkInt((Integer.MIN_VALUE - v) - 1);
      // Mul, And, Or, Xor, Shift chains
      checkInt((v * 2) * 10);
      checkInt((v * -1) * Integer.MIN_VALUE);
      checkInt((v & 10) & 2);
      checkInt((v | 10) | 2);
      checkInt((v ^ 10) ^ 10);
      checkInt((v << 1) << 2);
      checkInt((v << 10) << -10);
      checkInt((v >> 1) >> 2);
      checkInt((v >> 10) >> -10);
      checkInt((v >>> 1) >>> 2);
      checkInt((v >>> 10) >>> -10);
      // Nested min / max clamping
      checkInt(Math.min(Math.min(v, 10), -10));
      checkInt(Math.max(Math.max(v, -10), 10));
      checkInt(Math.min(Math.max(v, -10), 10));
      checkInt(Math.min(Math.max(v, 10), -10));
      checkInt(Math.max(Math.min(v, 10), -10));
      checkInt(Math.max(Math.min(v, -10), 10));
      checkInt(Integer.min(Math.max(v, -10), 10));
      checkInt(Integer.max(Math.min(v, 10), -10));
    }

    @NeverInline
    static void testLongBinopsAndStatics(long x) {
      long v = disguise(x);
      // 1. Add & Sub & Mul
      checkLong(v + Long.MIN_VALUE);
      checkLong(v + -10L);
      checkLong(v + -1L);
      checkLong(v + 0L);
      checkLong(v + 1L);
      checkLong(v + 2L);
      checkLong(v + 10L);
      checkLong(v + Long.MAX_VALUE);

      checkLong(v - Long.MIN_VALUE);
      checkLong(v - -10L);
      checkLong(v - -1L);
      checkLong(v - 0L);
      checkLong(v - 1L);
      checkLong(v - 2L);
      checkLong(v - 10L);
      checkLong(v - Long.MAX_VALUE);
      checkLong(0L - v);

      checkLong(v * Long.MIN_VALUE);
      checkLong(v * -10L);
      checkLong(v * -1L);
      checkLong(v * 0L);
      checkLong(v * 1L);
      checkLong(v * 2L);
      checkLong(v * 10L);
      checkLong(v * Long.MAX_VALUE);

      // 2. Div & Rem
      checkLong(v / Long.MIN_VALUE);
      checkLong(v / -10L);
      checkLong(v / -1L);
      try {
        checkLong(v / 0L);
      } catch (ArithmeticException e) {
        checkEx();
      }
      checkLong(v / 1L);
      checkLong(v / 2L);
      checkLong(v / 10L);
      checkLong(v / Long.MAX_VALUE);

      checkLong(v % Long.MIN_VALUE);
      checkLong(v % -10L);
      checkLong(v % -1L);
      try {
        checkLong(v % 0L);
      } catch (ArithmeticException e) {
        checkEx();
      }
      checkLong(v % 1L);
      checkLong(v % 2L);
      checkLong(v % 10L);
      checkLong(v % Long.MAX_VALUE);

      if (v != 0L) {
        checkLong(0L / v);
        checkLong(1L / v);
        checkLong(-1L / v);
        checkLong(0L % v);
        checkLong(1L % v);
        checkLong(-1L % v);
      }

      // 3. Bitwise & Shifts
      checkLong(v & Long.MIN_VALUE);
      checkLong(v & -1L);
      checkLong(v & 0L);
      checkLong(v & 1L);
      checkLong(v | Long.MIN_VALUE);
      checkLong(v | -1L);
      checkLong(v | 0L);
      checkLong(v ^ -1L);
      checkLong(v ^ 0L);

      checkLong(v << 0);
      checkLong(v << 1);
      checkLong(v << 2);
      checkLong(v << 10);
      checkLong(v << -1);
      checkLong(v >> 0);
      checkLong(v >> 1);
      checkLong(v >> 2);
      checkLong(v >> 10);
      checkLong(v >> -1);
      checkLong(v >>> 0);
      checkLong(v >>> 1);
      checkLong(v >>> 2);
      checkLong(v >>> 10);
      checkLong(v >>> -1);

      // 4. Static methods
      checkLong(Long.divideUnsigned(v, Long.MIN_VALUE));
      checkLong(Long.divideUnsigned(v, -10L));
      checkLong(Long.divideUnsigned(v, -1L));
      try {
        checkLong(Long.divideUnsigned(v, 0L));
      } catch (ArithmeticException e) {
        checkEx();
      }
      checkLong(Long.divideUnsigned(v, 1L));
      checkLong(Long.divideUnsigned(v, 2L));
      checkLong(Long.divideUnsigned(v, 10L));
      checkLong(Long.divideUnsigned(v, Long.MAX_VALUE));

      checkLong(Long.remainderUnsigned(v, Long.MIN_VALUE));
      checkLong(Long.remainderUnsigned(v, -10L));
      checkLong(Long.remainderUnsigned(v, -1L));
      try {
        checkLong(Long.remainderUnsigned(v, 0L));
      } catch (ArithmeticException e) {
        checkEx();
      }
      checkLong(Long.remainderUnsigned(v, 1L));
      checkLong(Long.remainderUnsigned(v, 2L));
      checkLong(Long.remainderUnsigned(v, 10L));
      checkLong(Long.remainderUnsigned(v, Long.MAX_VALUE));

      checkLong(Long.min(v, Long.MIN_VALUE));
      checkLong(Long.min(v, -10L));
      checkLong(Long.min(v, 0L));
      checkLong(Long.min(v, 10L));
      checkLong(Long.min(v, Long.MAX_VALUE));
      checkLong(Long.max(v, Long.MIN_VALUE));
      checkLong(Long.max(v, -10L));
      checkLong(Long.max(v, 0L));
      checkLong(Long.max(v, 10L));
      checkLong(Long.max(v, Long.MAX_VALUE));
      checkLong(Math.min(v, Long.MIN_VALUE));
      checkLong(Math.min(v, Long.MAX_VALUE));
      checkLong(Math.max(v, Long.MIN_VALUE));
      checkLong(Math.max(v, Long.MAX_VALUE));
      checkLong(Long.sum(v, 0L));
      checkLong(Long.sum(v, 10L));

      for (long c : LONG_VALUES) {
        try {
          checkLong(Math.addExact(v, c));
        } catch (ArithmeticException e) {
          checkEx();
        }
        try {
          checkLong(Math.subtractExact(v, c));
        } catch (ArithmeticException e) {
          checkEx();
        }
        try {
          checkLong(Math.multiplyExact(v, c));
        } catch (ArithmeticException e) {
          checkEx();
        }
      }
      checkLong(Math.addExact(v, 0L));
      checkLong(Math.subtractExact(v, 0L));
      checkLong(Math.multiplyExact(v, 0L));
      checkLong(Math.multiplyExact(v, 1L));

      checkLong(Math.floorDiv(v, Long.MIN_VALUE));
      checkLong(Math.floorDiv(v, -10L));
      checkLong(Math.floorDiv(v, -1L));
      try {
        checkLong(Math.floorDiv(v, 0L));
      } catch (ArithmeticException e) {
        checkEx();
      }
      checkLong(Math.floorDiv(v, 1L));
      checkLong(Math.floorDiv(v, 2L));
      checkLong(Math.floorDiv(v, 10L));
      checkLong(Math.floorDiv(v, Long.MAX_VALUE));

      checkLong(Math.floorMod(v, Long.MIN_VALUE));
      checkLong(Math.floorMod(v, -10L));
      checkLong(Math.floorMod(v, -1L));
      try {
        checkLong(Math.floorMod(v, 0L));
      } catch (ArithmeticException e) {
        checkEx();
      }
      checkLong(Math.floorMod(v, 1L));
      checkLong(Math.floorMod(v, 2L));
      checkLong(Math.floorMod(v, 10L));
      checkLong(Math.floorMod(v, Long.MAX_VALUE));
    }

    @NeverInline
    static void testLongSuccessive(long x, long y) {
      long v = disguise(x);
      checkLong((v + 10L) + -10L);
      checkLong((v + 10L) - 10L);
      checkLong((v - 10L) + 10L);
      checkLong((v - 10L) - -10L);
      checkLong((10L - v) + 2L);
      checkLong((10L - v) - 2L);
      checkLong((v * 2L) * 10L);
      checkLong((v & 10L) & 2L);
      checkLong((v | 10L) | 2L);
      checkLong((v ^ 10L) ^ 10L);
      checkLong((v << 1) << 2);
      checkLong((v >> 1) >> 2);
      checkLong((v >>> 1) >>> 2);
      checkLong(Math.min(Math.min(v, 10L), -10L));
      checkLong(Math.max(Math.max(v, -10L), 10L));
      checkLong(Math.min(Math.max(v, -10L), 10L));
      checkLong(Math.min(Math.max(v, 10L), -10L));
      checkLong(Math.max(Math.min(v, 10L), -10L));
      checkLong(Math.max(Math.min(v, -10L), 10L));
    }
  }

  /**
   * Regression test for {@code IntLongArithmeticRewriter.staticSimplify} removing {@code
   * InvokeStatic} instructions whose return value is unused ({@code !invokeStatic.hasOutValue()})
   * even when the modeled static method can throw {@link ArithmeticException}.
   */
  public static class UnusedThrowingStaticMain {

    static final int[] INT_VALUES = {Integer.MIN_VALUE, -10, -1, 0, 1, 2, 10, Integer.MAX_VALUE};

    static final long[] LONG_VALUES = {Long.MIN_VALUE, -10L, -1L, 0L, 1L, 2L, 10L, Long.MAX_VALUE};

    @NeverInline
    static void callDivideUnsignedInt(int a, int b) {
      Integer.divideUnsigned(a, b);
    }

    @NeverInline
    static void callRemainderUnsignedInt(int a, int b) {
      Integer.remainderUnsigned(a, b);
    }

    @NeverInline
    static void callFloorDivInt(int a, int b) {
      Math.floorDiv(a, b);
    }

    @NeverInline
    static void callFloorModInt(int a, int b) {
      Math.floorMod(a, b);
    }

    @NeverInline
    static void callAddExactInt(int a, int b) {
      Math.addExact(a, b);
    }

    @NeverInline
    static void callSubtractExactInt(int a, int b) {
      Math.subtractExact(a, b);
    }

    @NeverInline
    static void callMultiplyExactInt(int a, int b) {
      Math.multiplyExact(a, b);
    }

    @NeverInline
    static void callDivideUnsignedLong(long a, long b) {
      Long.divideUnsigned(a, b);
    }

    @NeverInline
    static void callRemainderUnsignedLong(long a, long b) {
      Long.remainderUnsigned(a, b);
    }

    @NeverInline
    static void callFloorDivLong(long a, long b) {
      Math.floorDiv(a, b);
    }

    @NeverInline
    static void callFloorModLong(long a, long b) {
      Math.floorMod(a, b);
    }

    @NeverInline
    static void callAddExactLong(long a, long b) {
      Math.addExact(a, b);
    }

    @NeverInline
    static void callSubtractExactLong(long a, long b) {
      Math.subtractExact(a, b);
    }

    @NeverInline
    static void callMultiplyExactLong(long a, long b) {
      Math.multiplyExact(a, b);
    }

    @NeverInline
    static int disguise(int v) {
      return v;
    }

    @NeverInline
    static long disguise(long v) {
      return v;
    }

    @NeverInline
    static void callUnusedIntWithConstantRight(int a) {
      int v = disguise(a);
      try {
        Integer.divideUnsigned(v, 0);
        System.out.println("OK");
      } catch (ArithmeticException e) {
        System.out.println("EX");
      }
      try {
        Integer.remainderUnsigned(v, 0);
        System.out.println("OK");
      } catch (ArithmeticException e) {
        System.out.println("EX");
      }
      try {
        Math.floorDiv(v, 0);
        System.out.println("OK");
      } catch (ArithmeticException e) {
        System.out.println("EX");
      }
      try {
        Math.floorMod(v, 0);
        System.out.println("OK");
      } catch (ArithmeticException e) {
        System.out.println("EX");
      }
      for (int c : INT_VALUES) {
        try {
          Math.addExact(v, c);
          System.out.println("OK");
        } catch (ArithmeticException e) {
          System.out.println("EX");
        }
        try {
          Math.subtractExact(v, c);
          System.out.println("OK");
        } catch (ArithmeticException e) {
          System.out.println("EX");
        }
        try {
          Math.multiplyExact(v, c);
          System.out.println("OK");
        } catch (ArithmeticException e) {
          System.out.println("EX");
        }
      }
    }

    @NeverInline
    static void callUnusedLongWithConstantRight(long a) {
      long v = disguise(a);
      try {
        Long.divideUnsigned(v, 0L);
        System.out.println("OK");
      } catch (ArithmeticException e) {
        System.out.println("EX");
      }
      try {
        Long.remainderUnsigned(v, 0L);
        System.out.println("OK");
      } catch (ArithmeticException e) {
        System.out.println("EX");
      }
      try {
        Math.floorDiv(v, 0L);
        System.out.println("OK");
      } catch (ArithmeticException e) {
        System.out.println("EX");
      }
      try {
        Math.floorMod(v, 0L);
        System.out.println("OK");
      } catch (ArithmeticException e) {
        System.out.println("EX");
      }
      for (long c : LONG_VALUES) {
        try {
          Math.addExact(v, c);
          System.out.println("OK");
        } catch (ArithmeticException e) {
          System.out.println("EX");
        }
        try {
          Math.subtractExact(v, c);
          System.out.println("OK");
        } catch (ArithmeticException e) {
          System.out.println("EX");
        }
        try {
          Math.multiplyExact(v, c);
          System.out.println("OK");
        } catch (ArithmeticException e) {
          System.out.println("EX");
        }
      }
    }

    public static void main(String[] args) {
      for (int a : INT_VALUES) {
        callUnusedIntWithConstantRight(a);
        for (int b : INT_VALUES) {
          try {
            callDivideUnsignedInt(a, b);
            System.out.println("OK");
          } catch (ArithmeticException e) {
            System.out.println("EX");
          }
          try {
            callRemainderUnsignedInt(a, b);
            System.out.println("OK");
          } catch (ArithmeticException e) {
            System.out.println("EX");
          }
          try {
            callFloorDivInt(a, b);
            System.out.println("OK");
          } catch (ArithmeticException e) {
            System.out.println("EX");
          }
          try {
            callFloorModInt(a, b);
            System.out.println("OK");
          } catch (ArithmeticException e) {
            System.out.println("EX");
          }
          try {
            callAddExactInt(a, b);
            System.out.println("OK");
          } catch (ArithmeticException e) {
            System.out.println("EX");
          }
          try {
            callSubtractExactInt(a, b);
            System.out.println("OK");
          } catch (ArithmeticException e) {
            System.out.println("EX");
          }
          try {
            callMultiplyExactInt(a, b);
            System.out.println("OK");
          } catch (ArithmeticException e) {
            System.out.println("EX");
          }
        }
      }

      for (long a : LONG_VALUES) {
        callUnusedLongWithConstantRight(a);
        for (long b : LONG_VALUES) {
          try {
            callDivideUnsignedLong(a, b);
            System.out.println("OK");
          } catch (ArithmeticException e) {
            System.out.println("EX");
          }
          try {
            callRemainderUnsignedLong(a, b);
            System.out.println("OK");
          } catch (ArithmeticException e) {
            System.out.println("EX");
          }
          try {
            callFloorDivLong(a, b);
            System.out.println("OK");
          } catch (ArithmeticException e) {
            System.out.println("EX");
          }
          try {
            callFloorModLong(a, b);
            System.out.println("OK");
          } catch (ArithmeticException e) {
            System.out.println("EX");
          }
          try {
            callAddExactLong(a, b);
            System.out.println("OK");
          } catch (ArithmeticException e) {
            System.out.println("EX");
          }
          try {
            callSubtractExactLong(a, b);
            System.out.println("OK");
          } catch (ArithmeticException e) {
            System.out.println("EX");
          }
          try {
            callMultiplyExactLong(a, b);
            System.out.println("OK");
          } catch (ArithmeticException e) {
            System.out.println("EX");
          }
        }
      }
    }
  }

  /**
   * Tests nested {@code min}/{@code max} clamping when the outer bound comes from a {@code Phi}
   * whose operands are simplified to identical constants in predecessor branches by {@code
   * IntLongArithmeticRewriter} ({@code getConstNumber(Phi)} in {@code optimizeMinMax}).
   */
  public static class MinMaxPhiMain {

    static final int[] INT_VALUES = {Integer.MIN_VALUE, -10, -1, 0, 1, 2, 10, Integer.MAX_VALUE};

    @NeverInline
    static int disguise(int v) {
      return v;
    }

    @NeverInline
    static int clampMinMaxPhi(int x) {
      int c2;
      if (x == 42) {
        disguise(1);
        c2 = Math.multiplyExact(x, 0);
      } else {
        disguise(2);
        c2 = Math.multiplyExact(0, x);
      }
      int res = Math.min(Math.max(x, 10), c2);
      disguise(res);
      return res;
    }

    @NeverInline
    static int clampMaxMinPhi(int x) {
      int c2;
      if (x == 42) {
        disguise(1);
        c2 = Math.multiplyExact(x, 0);
      } else {
        disguise(2);
        c2 = Math.multiplyExact(0, x);
      }
      int res = Math.max(Math.min(x, -10), c2);
      disguise(res);
      return res;
    }

    public static void main(String[] args) {
      for (int a : INT_VALUES) {
        System.out.println(clampMinMaxPhi(a));
        System.out.println(clampMaxMinPhi(a));
      }
    }
  }
}
