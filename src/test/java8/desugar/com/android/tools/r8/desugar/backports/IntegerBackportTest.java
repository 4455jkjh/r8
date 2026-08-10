// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.math.BigInteger;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class IntegerBackportTest extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public IntegerBackportTest(TestParameters parameters) {
    super(parameters, Integer.class, Main.class);
    registerTarget(AndroidApiLevel.N, 11);
    registerTarget(AndroidApiLevel.K, 7);
  }

  @Override
  protected void configureProgram(TestBuilder<?, ?> builder) throws Exception {
    super.configureProgram(builder);
    if (builder.isR8TestBuilder()) {
      R8TestBuilder<?, ?, ?> r8Builder = builder.asR8TestBuilder();
      r8Builder.addKeepRules("-keepclassmembers class * { int disguise(int); }");
    }
  }

  @Override
  public void testD8() throws Exception {
    registerTarget(AndroidApiLevel.O, 51);
    super.testD8();
  }

  @Override
  public void testD8Cf() throws Exception {
    registerTarget(AndroidApiLevel.O, 51);
    super.testD8Cf();
  }

  @Override
  public void testR8() throws Exception {
    registerTarget(AndroidApiLevel.O, 50);
    super.testR8();
  }

  static final class Main extends MiniAssert {
    private static final int[] interestingValues = {
        Integer.MIN_VALUE, Integer.MAX_VALUE,
        Short.MIN_VALUE, Short.MAX_VALUE,
        Byte.MIN_VALUE, Byte.MAX_VALUE,
        0,
        -1, 1,
        -42, 42
    };

    public static void main(String[] args) {
      testHashCode();
      testToUnsignedLong();
      testCompare();
      testMax();
      testMin();
      testSum();
      testCompareUnsigned();
      testDivideUnsigned();
      testRemainderUnsigned();
      testParseUnsignedInt();
      testParseUnsignedIntWithRadix();
      testToUnsignedString();
      testToUnsignedStringWithRadix();
    }

    private static int disguise(int i) {
      return i;
    }

    private static void testHashCode() {
      for (int value : interestingValues) {
        assertEquals(value, Integer.hashCode(value));
      }
    }

    private static void testToUnsignedLong() {
      assertEquals(0L, Integer.toUnsignedLong(0));
      assertEquals(2_147_483_647L, Integer.toUnsignedLong(disguise(Integer.MAX_VALUE)));
      assertEquals(2_147_483_648L, Integer.toUnsignedLong(disguise(Integer.MIN_VALUE)));
      assertEquals(4_294_967_295L, Integer.toUnsignedLong(disguise(-1)));
    }

    private static void testCompare() {
      assertTrue(Integer.compare(disguise(1), disguise(0)) > 0);
      assertTrue(Integer.compare(disguise(0), disguise(0)) == 0);
      assertTrue(Integer.compare(disguise(0), disguise(1)) < 0);
      assertTrue(Integer.compare(disguise(Integer.MIN_VALUE), disguise(Integer.MAX_VALUE)) < 0);
      assertTrue(Integer.compare(disguise(Integer.MAX_VALUE), disguise(Integer.MIN_VALUE)) > 0);
      assertTrue(Integer.compare(disguise(Integer.MIN_VALUE), disguise(Integer.MIN_VALUE)) == 0);
      assertTrue(Integer.compare(disguise(Integer.MAX_VALUE), disguise(Integer.MAX_VALUE)) == 0);
    }

    private static void testMax() {
      for (int x : interestingValues) {
        for (int y : interestingValues) {
          assertEquals(Math.max(x, y), Integer.max(x, y));
        }
      }
    }

    private static void testMin() {
      for (int x : interestingValues) {
        for (int y : interestingValues) {
          assertEquals(Math.min(x, y), Integer.min(x, y));
        }
      }
    }

    private static void testSum() {
      for (int x : interestingValues) {
        for (int y : interestingValues) {
          assertEquals(x + y, Integer.sum(x, y));
        }
      }
    }

    private static void testCompareUnsigned() {
      assertTrue(Integer.compareUnsigned(disguise(0), disguise(0)) == 0);
      assertTrue(Integer.compareUnsigned(disguise(0), disguise(Integer.MAX_VALUE)) < 0);
      assertTrue(Integer.compareUnsigned(disguise(0), disguise(Integer.MIN_VALUE)) < 0);
      assertTrue(Integer.compareUnsigned(disguise(0), disguise(-1)) < 0);

      assertTrue(Integer.compareUnsigned(disguise(Integer.MAX_VALUE), disguise(0)) > 0);
      assertTrue(
          Integer.compareUnsigned(disguise(Integer.MAX_VALUE), disguise(Integer.MAX_VALUE)) == 0);
      assertTrue(
          Integer.compareUnsigned(disguise(Integer.MAX_VALUE), disguise(Integer.MIN_VALUE)) < 0);
      assertTrue(Integer.compareUnsigned(disguise(Integer.MAX_VALUE), disguise(-1)) < 0);

      assertTrue(Integer.compareUnsigned(disguise(Integer.MIN_VALUE), disguise(0)) > 0);
      assertTrue(
          Integer.compareUnsigned(disguise(Integer.MIN_VALUE), disguise(Integer.MAX_VALUE)) > 0);
      assertTrue(
          Integer.compareUnsigned(disguise(Integer.MIN_VALUE), disguise(Integer.MIN_VALUE)) == 0);
      assertTrue(Integer.compareUnsigned(disguise(Integer.MIN_VALUE), disguise(-1)) < 0);

      assertTrue(Integer.compareUnsigned(disguise(-1), disguise(0)) > 0);
      assertTrue(Integer.compareUnsigned(disguise(-1), disguise(Integer.MAX_VALUE)) > 0);
      assertTrue(Integer.compareUnsigned(disguise(-1), disguise(Integer.MIN_VALUE)) > 0);
      assertTrue(Integer.compareUnsigned(disguise(-1), disguise(-1)) == 0);
    }

    private static void testDivideUnsigned() {
      for (int x : interestingValues) {
        for (int y : interestingValues) {
          if (y == 0) continue;

          BigInteger xUnsigned = BigInteger.valueOf(x & 0xffffffffL);
          BigInteger yUnsigned = BigInteger.valueOf(y & 0xffffffffL);
          int expected = xUnsigned.divide(yUnsigned).intValue();

          assertEquals(expected, Integer.divideUnsigned(x, y));
        }
      }

      try {
        throw new AssertionError(Integer.divideUnsigned(1, 0));
      } catch (ArithmeticException expected) {
      }
    }

    private static void testRemainderUnsigned() {
      for (int x : interestingValues) {
        for (int y : interestingValues) {
          if (y == 0) continue;

          BigInteger xUnsigned = BigInteger.valueOf(x & 0xffffffffL);
          BigInteger yUnsigned = BigInteger.valueOf(y & 0xffffffffL);
          int expected = xUnsigned.remainder(yUnsigned).intValue();

          assertEquals(expected, Integer.remainderUnsigned(x, y));
        }
      }

      try {
        throw new AssertionError(Integer.divideUnsigned(1, 0));
      } catch (ArithmeticException expected) {
      }
    }

    private static void testParseUnsignedInt() {
      for (int value : interestingValues) {
        String valueString = Long.toString(value & 0xffffffffL);
        assertEquals(value, Integer.parseUnsignedInt(valueString));
        assertEquals(value, Integer.parseUnsignedInt("+" + valueString));
      }

      try {
        throw new AssertionError(Integer.parseUnsignedInt("4294967296"));
      } catch (NumberFormatException expected) {
      }
      try {
        throw new AssertionError(Integer.parseUnsignedInt(""));
      } catch (NumberFormatException expected) {
      }
      try {
        throw new AssertionError(Integer.parseUnsignedInt("+"));
      } catch (NumberFormatException expected) {
      }
    }

    private static void testParseUnsignedIntWithRadix() {
      for (int value : interestingValues) {
        for (int radix = Character.MIN_RADIX; radix <= Character.MAX_RADIX; radix++) {
          String valueString = Long.toString(value & 0xffffffffL, radix);
          assertEquals(value, Integer.parseUnsignedInt(valueString, radix));
          assertEquals(value, Integer.parseUnsignedInt("+" + valueString, radix));
        }
      }

      try {
        throw new AssertionError(Integer.parseUnsignedInt("0", Character.MIN_RADIX - 1));
      } catch (IllegalArgumentException expected) {
      }
      try {
        throw new AssertionError(Integer.parseUnsignedInt("0", Character.MAX_RADIX + 1));
      } catch (IllegalArgumentException expected) {
      }

      try {
        throw new AssertionError(Integer.parseUnsignedInt("", 16));
      } catch (NumberFormatException expected) {
      }
      try {
        throw new AssertionError(Integer.parseUnsignedInt("+", 16));
      } catch (NumberFormatException expected) {
      }

      long overflow = 4_294_967_296L;
      for (int radix = Character.MIN_RADIX; radix <= Character.MAX_RADIX; radix++) {
        String overflowString = Long.toString(overflow, radix);
        try {
          throw new AssertionError(Integer.parseUnsignedInt(overflowString, radix));
        } catch (NumberFormatException expected) {
        }
      }
    }

    private static void testToUnsignedString() {
      for (int value : interestingValues) {
        assertEquals(Long.toString(value & 0xffffffffL), Integer.toUnsignedString(value));
      }
    }

    private static void testToUnsignedStringWithRadix() {
      for (int value : interestingValues) {
        for (int radix = Character.MIN_RADIX; radix <= Character.MAX_RADIX; radix++) {
          assertEquals(Long.toString(value & 0xffffffffL, radix),
              Integer.toUnsignedString(value, radix));
        }

        // Invalid radix values are ignored and 10 is used.
        assertEquals(Long.toString(value & 0xffffffffL),
            Integer.toUnsignedString(value, Character.MIN_RADIX - 1));
        assertEquals(Long.toString(value & 0xffffffffL),
            Integer.toUnsignedString(value, Character.MAX_RADIX + 1));
      }
    }
  }
}
