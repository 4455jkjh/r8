// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.desugar.backports.AbstractBackportTest;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class StrictMathJava21Test extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters()
        .withDexRuntimes()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK21)
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public StrictMathJava21Test(TestParameters parameters) {
    super(parameters, StrictMath.class, Main.class);
    registerTarget(AndroidApiLevel.V, 70);
  }

  static final class Main extends MiniAssert {

    public static void main(String[] args) {
      testClampInt();
      testClampLong();
      testClampDouble();
      testClampFloat();

      testCeilDivIntInt();
      testCeilDivIntIntExact();
      testCeilDivLongLong();
      testCeilDivLongLongExact();
      testCeilDivLongInt();

      testCeilModIntInt();
      testCeilModLongLong();
      testCeilModLongInt();
    }

    private static void testClampInt() {
      assertEquals(1, StrictMath.clamp(1, 0, 5));
      assertEquals(0, StrictMath.clamp(-1, 0, 5));
      assertEquals(5, StrictMath.clamp(10, 0, 5));
      try {
        StrictMath.clamp(1, 10, 5);
        fail("Should have thrown");
      } catch (IllegalArgumentException ignored) {
      }
    }

    private static void testClampLong() {
      assertEquals(1, StrictMath.clamp(1L, 0L, 5L));
      assertEquals(0, StrictMath.clamp(-1L, 0L, 5L));
      assertEquals(5, StrictMath.clamp(10L, 0L, 5L));
      try {
        StrictMath.clamp(1L, 10L, 5L);
        fail("Should have thrown");
      } catch (IllegalArgumentException ignored) {
      }
    }

    private static void testClampDouble() {
      assertEquals(1.0, StrictMath.clamp(1.0, 0.0, 5.0));
      assertEquals(0.0, StrictMath.clamp(-1.0, 0.0, 5.0));
      assertEquals(5.0, StrictMath.clamp(10.0, 0.0, 5.0));
      try {
        StrictMath.clamp(1.0, 10.0, 5.0);
        fail("Should have thrown");
      } catch (IllegalArgumentException ignored) {

      }
      // Check for -/+0.0.
      assertEquals(0.0, StrictMath.clamp(-0.0, 0.0, 1.0));
      assertEquals(0.0, StrictMath.clamp(0.0, -0.0, 1.0));
      assertEquals(-0.0, StrictMath.clamp(-0.0, -1.0, 0.0));
      assertEquals(-0.0, StrictMath.clamp(0.0, -1.0, -0.0));
      // Check for NaN.
      assertEquals(Double.NaN, StrictMath.clamp(Double.NaN, 0.0, 1.0));
      try {
        StrictMath.clamp(1.0, Double.NaN, 5.0);
        fail("Should have thrown");
      } catch (IllegalArgumentException ignored) {
      }
      try {
        StrictMath.clamp(1.0, 10.0, Double.NaN);
        fail("Should have thrown");
      } catch (IllegalArgumentException ignored) {
      }
    }

    private static void testClampFloat() {
      assertEquals(1.0f, StrictMath.clamp(1.0f, 0.0f, 5.0f));
      assertEquals(0.0f, StrictMath.clamp(-1.0f, 0.0f, 5.0f));
      assertEquals(5.0f, StrictMath.clamp(10.0f, 0.0f, 5.0f));
      try {
        StrictMath.clamp(1.0f, 10.0f, 5.0f);
        fail("Should have thrown");
      } catch (IllegalArgumentException ignored) {

      }
      // Check for -/+0.0f.
      assertEquals(0.0f, StrictMath.clamp(-0.0f, 0.0f, 1.0f));
      assertEquals(0.0f, StrictMath.clamp(0.0f, -0.0f, 1.0f));
      assertEquals(-0.0f, StrictMath.clamp(-0.0f, -1.0f, 0.0f));
      assertEquals(-0.0f, StrictMath.clamp(0.0f, -1.0f, -0.0f));
      // Check for NaN.
      assertEquals(Float.NaN, StrictMath.clamp(Float.NaN, 0.0f, 1.0f));
      try {
        StrictMath.clamp(1.0f, Float.NaN, 5.0f);
        fail("Should have thrown");
      } catch (IllegalArgumentException ignored) {
      }
      try {
        StrictMath.clamp(1.0f, 10.0f, Float.NaN);
        fail("Should have thrown");
      } catch (IllegalArgumentException ignored) {
      }
    }

    private static void testCeilDivIntInt() {
      assertEquals(1, StrictMath.ceilDiv(7, 7));
      assertEquals(-1, StrictMath.ceilDiv(-7, 7));
      assertEquals(1, StrictMath.ceilDiv(3, 7));
      assertEquals(0, StrictMath.ceilDiv(-3, 7));
      assertEquals(-2147483648, StrictMath.ceilDiv(Integer.MIN_VALUE, -1));
    }

    private static void testCeilDivIntIntExact() {
      assertEquals(1, StrictMath.ceilDivExact(7, 7));
      assertEquals(-1, StrictMath.ceilDivExact(-7, 7));
      assertEquals(1, StrictMath.ceilDivExact(3, 7));
      assertEquals(0, StrictMath.ceilDivExact(-3, 7));
      try {
        StrictMath.ceilDivExact(Integer.MIN_VALUE, -1);
        fail("Should have thrown");
      } catch (ArithmeticException ae) {

      }
    }

    private static void testCeilDivLongLong() {
      assertEquals(1L, StrictMath.ceilDiv(7L, 7L));
      assertEquals(-1L, StrictMath.ceilDiv(-7L, 7L));
      assertEquals(1L, StrictMath.ceilDiv(3L, 7L));
      assertEquals(0, StrictMath.ceilDiv(-3L, 7L));
      assertEquals(-9223372036854775808L, StrictMath.ceilDiv(Long.MIN_VALUE, -1L));
    }

    private static void testCeilDivLongLongExact() {
      assertEquals(1L, StrictMath.ceilDivExact(7L, 7L));
      assertEquals(-1L, StrictMath.ceilDivExact(-7L, 7L));
      assertEquals(1L, StrictMath.ceilDivExact(3L, 7L));
      assertEquals(0, StrictMath.ceilDivExact(-3L, 7L));
      try {
        StrictMath.ceilDivExact(Long.MIN_VALUE, -1L);
        fail("Should have thrown");
      } catch (ArithmeticException ae) {

      }
    }

    private static void testCeilDivLongInt() {
      assertEquals(1L, StrictMath.ceilDiv(7L, 7));
      assertEquals(-1L, StrictMath.ceilDiv(-7L, 7));
      assertEquals(1L, StrictMath.ceilDiv(3L, 7));
      assertEquals(0, StrictMath.ceilDiv(-3L, 7));
      assertEquals(-9223372036854775808L, StrictMath.ceilDiv(Long.MIN_VALUE, -1));
    }

    private static void testCeilModIntInt() {
      assertEquals(0, StrictMath.ceilMod(7, 7));
      assertEquals(0, StrictMath.ceilMod(-7, 7));
      assertEquals(-4, StrictMath.ceilMod(3, 7));
      assertEquals(-3, StrictMath.ceilMod(-3, 7));
      assertEquals(0, StrictMath.ceilMod(Integer.MIN_VALUE, -1));
    }

    private static void testCeilModLongLong() {
      assertEquals(0L, StrictMath.ceilMod(7L, 7L));
      assertEquals(0L, StrictMath.ceilMod(-7L, 7L));
      assertEquals(-4L, StrictMath.ceilMod(3L, 7L));
      assertEquals(-3L, StrictMath.ceilMod(-3L, 7L));
      assertEquals(0L, StrictMath.ceilMod(Long.MIN_VALUE, -1L));
    }

    private static void testCeilModLongInt() {
      assertEquals(0L, StrictMath.ceilMod(7L, 7));
      assertEquals(0L, StrictMath.ceilMod(-7L, 7));
      assertEquals(-4L, StrictMath.ceilMod(3L, 7));
      assertEquals(-3L, StrictMath.ceilMod(-3L, 7));
      assertEquals(0L, StrictMath.ceilMod(Long.MIN_VALUE, -1));
    }
  }
}
