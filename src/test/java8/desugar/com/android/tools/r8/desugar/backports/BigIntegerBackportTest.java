// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.math.BigInteger;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class BigIntegerBackportTest extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public BigIntegerBackportTest(TestParameters parameters) {
    super(parameters, BigInteger.class, Main.class);
    registerTarget(AndroidApiLevel.S, 7);
    ignoreInvokes("valueOf");
    ignoreInvokes("add");
    ignoreInvokes("subtract");
  }

  static final class Main extends MiniAssert {
    public static void main(String[] args) {
      testIntValueExact();
    }

    private static void testIntValueExact() {
      assertEquals(0, BigInteger.valueOf(0).intValueExact());
      assertEquals(1, BigInteger.valueOf(1).intValueExact());
      assertEquals(-1, BigInteger.valueOf(-1).intValueExact());
      assertEquals(Integer.MAX_VALUE, BigInteger.valueOf(Integer.MAX_VALUE).intValueExact());
      assertEquals(Integer.MIN_VALUE, BigInteger.valueOf(Integer.MIN_VALUE).intValueExact());

      try {
        BigInteger.valueOf(Integer.MAX_VALUE).add(BigInteger.valueOf(1)).intValueExact();
        fail("Expected ArithmeticException");
      } catch (ArithmeticException expected) {
      }

      try {
        BigInteger.valueOf(Integer.MIN_VALUE).subtract(BigInteger.valueOf(1)).intValueExact();
        fail("Expected ArithmeticException");
      } catch (ArithmeticException expected) {
      }
    }
  }
}
