// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk9.backport;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.desugar.backports.AbstractBackportTest;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.Arrays;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class ArraysCompareUnsignedBackportJava9Test extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return TestBase.getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public ArraysCompareUnsignedBackportJava9Test(TestParameters parameters) {
    super(parameters, Arrays.class, Main.class);

    // Arrays.compareUnsigned methods were added in Android T (API 33).
    registerTarget(AndroidApiLevel.T, 119);
  }

  public static class Main extends MiniAssert {

    public static void main(String[] args) {
      testCompareUnsignedByte();
      testCompareUnsignedByteRange();
      testCompareUnsignedShort();
      testCompareUnsignedShortRange();
      testCompareUnsignedInt();
      testCompareUnsignedIntRange();
      testCompareUnsignedLong();
      testCompareUnsignedLongRange();
    }

    private static void testCompareUnsignedByte() {
      byte[] nullArray = System.currentTimeMillis() > 0 ? null : new byte[0];

      assertTrue(Arrays.compareUnsigned(nullArray, nullArray) == 0);
      assertTrue(Arrays.compareUnsigned(nullArray, new byte[0]) < 0);
      assertTrue(Arrays.compareUnsigned(new byte[0], nullArray) > 0);

      byte[] a = new byte[] {1, 2, 3};
      assertTrue(Arrays.compareUnsigned(a, a) == 0);
      assertTrue(Arrays.compareUnsigned(new byte[0], new byte[0]) == 0);
      assertTrue(Arrays.compareUnsigned(new byte[] {1, 2, 3}, new byte[] {1, 2, 3}) == 0);
      assertTrue(
          Arrays.compareUnsigned(
                  new byte[] {Byte.MIN_VALUE, -1, 0, 1, Byte.MAX_VALUE},
                  new byte[] {Byte.MIN_VALUE, -1, 0, 1, Byte.MAX_VALUE})
              == 0);

      assertTrue(Arrays.compareUnsigned(new byte[] {1, 2}, new byte[] {1, 2, 3}) < 0);
      assertTrue(Arrays.compareUnsigned(new byte[] {1, 2, 3}, new byte[] {1, 2}) > 0);

      assertTrue(Arrays.compareUnsigned(new byte[] {0}, new byte[] {1}) < 0);
      assertTrue(Arrays.compareUnsigned(new byte[] {1}, new byte[] {Byte.MAX_VALUE}) < 0);
      assertTrue(
          Arrays.compareUnsigned(new byte[] {Byte.MAX_VALUE}, new byte[] {Byte.MIN_VALUE}) < 0);
      assertTrue(Arrays.compareUnsigned(new byte[] {Byte.MIN_VALUE}, new byte[] {(byte) -1}) < 0);
      byte[] minusOne = new byte[] {(byte) -1};
      byte[] zero = new byte[] {0};
      byte[] max = new byte[] {Byte.MAX_VALUE};
      byte[] min = new byte[] {Byte.MIN_VALUE};
      assertTrue(Arrays.compareUnsigned(minusOne, zero) > 0);
      assertTrue(Arrays.compareUnsigned(minusOne, max) > 0);
      assertTrue(Arrays.compareUnsigned(minusOne, min) > 0);
      assertTrue(Arrays.compareUnsigned(new byte[] {1, 2, (byte) -1}, new byte[] {1, 2, 0}) > 0);
    }

    private static void testCompareUnsignedByteRange() {
      byte[] nullArray = System.currentTimeMillis() > 0 ? null : new byte[0];
      byte[] a = new byte[] {-3, -2, -1, 0, 1, 2, 3};
      byte[] b = new byte[] {Byte.MIN_VALUE, -1, 0, 1, Byte.MAX_VALUE};

      assertTrue(Arrays.compareUnsigned(new byte[] {}, 0, 0, new byte[] {}, 0, 0) == 0);
      assertTrue(Arrays.compareUnsigned(a, 0, 1, a, 0, 1) == 0);
      assertTrue(Arrays.compareUnsigned(a, 2, 5, b, 1, 4) == 0);
      assertTrue(Arrays.compareUnsigned(a, 1, 1, b, 2, 2) == 0);

      assertTrue(Arrays.compareUnsigned(a, 2, 3, b, 2, 3) > 0);
      assertTrue(Arrays.compareUnsigned(b, 2, 3, a, 2, 3) < 0);
      assertTrue(Arrays.compareUnsigned(a, 2, 4, b, 1, 4) < 0);
      assertTrue(Arrays.compareUnsigned(b, 1, 4, a, 2, 4) > 0);

      try {
        Arrays.compareUnsigned(nullArray, 0, 1, b, 1, 2);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.compareUnsigned(a, 0, 1, nullArray, 1, 2);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.compareUnsigned(a, 2, 1, b, 1, 2);
        fail("Expected IllegalArgumentException");
      } catch (IllegalArgumentException expected) {
      }
      try {
        Arrays.compareUnsigned(a, -1, 1, b, 1, 2);
        fail("Expected ArrayIndexOutOfBoundsException");
      } catch (ArrayIndexOutOfBoundsException expected) {
      }
      try {
        Arrays.compareUnsigned(a, 0, 10, b, 1, 2);
        fail("Expected ArrayIndexOutOfBoundsException");
      } catch (ArrayIndexOutOfBoundsException expected) {
      }
    }

    private static void testCompareUnsignedShort() {
      short[] nullArray = System.currentTimeMillis() > 0 ? null : new short[0];

      assertTrue(Arrays.compareUnsigned(nullArray, nullArray) == 0);
      assertTrue(Arrays.compareUnsigned(nullArray, new short[0]) < 0);
      assertTrue(Arrays.compareUnsigned(new short[0], nullArray) > 0);

      short[] a = new short[] {1, 2, 3};
      assertTrue(Arrays.compareUnsigned(a, a) == 0);
      assertTrue(Arrays.compareUnsigned(new short[0], new short[0]) == 0);
      assertTrue(Arrays.compareUnsigned(new short[] {1, 2, 3}, new short[] {1, 2, 3}) == 0);
      assertTrue(
          Arrays.compareUnsigned(
                  new short[] {Short.MIN_VALUE, -1, 0, 1, Short.MAX_VALUE},
                  new short[] {Short.MIN_VALUE, -1, 0, 1, Short.MAX_VALUE})
              == 0);

      assertTrue(Arrays.compareUnsigned(new short[] {1, 2}, new short[] {1, 2, 3}) < 0);
      assertTrue(Arrays.compareUnsigned(new short[] {1, 2, 3}, new short[] {1, 2}) > 0);

      assertTrue(Arrays.compareUnsigned(new short[] {0}, new short[] {1}) < 0);
      assertTrue(Arrays.compareUnsigned(new short[] {1}, new short[] {Short.MAX_VALUE}) < 0);
      assertTrue(
          Arrays.compareUnsigned(new short[] {Short.MAX_VALUE}, new short[] {Short.MIN_VALUE}) < 0);
      short[] minusOne = new short[] {(short) -1};
      short[] zero = new short[] {0};
      short[] max = new short[] {Short.MAX_VALUE};
      short[] min = new short[] {Short.MIN_VALUE};
      assertTrue(Arrays.compareUnsigned(minusOne, zero) > 0);
      assertTrue(Arrays.compareUnsigned(minusOne, max) > 0);
      assertTrue(Arrays.compareUnsigned(minusOne, min) > 0);
      assertTrue(Arrays.compareUnsigned(new short[] {1, 2, (short) -1}, new short[] {1, 2, 0}) > 0);
    }

    private static void testCompareUnsignedShortRange() {
      short[] nullArray = System.currentTimeMillis() > 0 ? null : new short[0];
      short[] a = new short[] {-3, -2, -1, 0, 1, 2, 3};
      short[] b = new short[] {Short.MIN_VALUE, -1, 0, 1, Short.MAX_VALUE};

      assertTrue(Arrays.compareUnsigned(new short[] {}, 0, 0, new short[] {}, 0, 0) == 0);
      assertTrue(Arrays.compareUnsigned(a, 0, 1, a, 0, 1) == 0);
      assertTrue(Arrays.compareUnsigned(a, 2, 5, b, 1, 4) == 0);
      assertTrue(Arrays.compareUnsigned(a, 1, 1, b, 2, 2) == 0);

      assertTrue(Arrays.compareUnsigned(a, 2, 3, b, 2, 3) > 0);
      assertTrue(Arrays.compareUnsigned(b, 2, 3, a, 2, 3) < 0);
      assertTrue(Arrays.compareUnsigned(a, 2, 4, b, 1, 4) < 0);
      assertTrue(Arrays.compareUnsigned(b, 1, 4, a, 2, 4) > 0);

      try {
        Arrays.compareUnsigned(nullArray, 0, 1, b, 1, 2);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.compareUnsigned(a, 0, 1, nullArray, 1, 2);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.compareUnsigned(a, 2, 1, b, 1, 2);
        fail("Expected IllegalArgumentException");
      } catch (IllegalArgumentException expected) {
      }
      try {
        Arrays.compareUnsigned(a, -1, 1, b, 1, 2);
        fail("Expected ArrayIndexOutOfBoundsException");
      } catch (ArrayIndexOutOfBoundsException expected) {
      }
      try {
        Arrays.compareUnsigned(a, 0, 10, b, 1, 2);
        fail("Expected ArrayIndexOutOfBoundsException");
      } catch (ArrayIndexOutOfBoundsException expected) {
      }
    }

    private static void testCompareUnsignedInt() {
      int[] nullArray = System.currentTimeMillis() > 0 ? null : new int[0];

      assertTrue(Arrays.compareUnsigned(nullArray, nullArray) == 0);
      assertTrue(Arrays.compareUnsigned(nullArray, new int[0]) < 0);
      assertTrue(Arrays.compareUnsigned(new int[0], nullArray) > 0);

      int[] a = new int[] {1, 2, 3};
      assertTrue(Arrays.compareUnsigned(a, a) == 0);
      assertTrue(Arrays.compareUnsigned(new int[0], new int[0]) == 0);
      assertTrue(Arrays.compareUnsigned(new int[] {1, 2, 3}, new int[] {1, 2, 3}) == 0);
      assertTrue(
          Arrays.compareUnsigned(
                  new int[] {Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE},
                  new int[] {Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE})
              == 0);

      assertTrue(Arrays.compareUnsigned(new int[] {1, 2}, new int[] {1, 2, 3}) < 0);
      assertTrue(Arrays.compareUnsigned(new int[] {1, 2, 3}, new int[] {1, 2}) > 0);

      assertTrue(Arrays.compareUnsigned(new int[] {0}, new int[] {1}) < 0);
      assertTrue(Arrays.compareUnsigned(new int[] {1}, new int[] {Integer.MAX_VALUE}) < 0);
      assertTrue(
          Arrays.compareUnsigned(new int[] {Integer.MAX_VALUE}, new int[] {Integer.MIN_VALUE}) < 0);
      assertTrue(Arrays.compareUnsigned(new int[] {Integer.MIN_VALUE}, new int[] {-1}) < 0);
      assertTrue(Arrays.compareUnsigned(new int[] {-1}, new int[] {0}) > 0);
      assertTrue(Arrays.compareUnsigned(new int[] {-1}, new int[] {Integer.MAX_VALUE}) > 0);
      assertTrue(Arrays.compareUnsigned(new int[] {-1}, new int[] {Integer.MIN_VALUE}) > 0);
      assertTrue(Arrays.compareUnsigned(new int[] {1, 2, -1}, new int[] {1, 2, 0}) > 0);
    }

    private static void testCompareUnsignedIntRange() {
      int[] nullArray = System.currentTimeMillis() > 0 ? null : new int[0];
      int[] a = new int[] {-3, -2, -1, 0, 1, 2, 3};
      int[] b = new int[] {Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE};

      assertTrue(Arrays.compareUnsigned(new int[] {}, 0, 0, new int[] {}, 0, 0) == 0);
      assertTrue(Arrays.compareUnsigned(a, 0, 1, a, 0, 1) == 0);
      assertTrue(Arrays.compareUnsigned(a, 2, 5, b, 1, 4) == 0);
      assertTrue(Arrays.compareUnsigned(a, 1, 1, b, 2, 2) == 0);

      assertTrue(Arrays.compareUnsigned(a, 2, 3, b, 2, 3) > 0);
      assertTrue(Arrays.compareUnsigned(b, 2, 3, a, 2, 3) < 0);
      assertTrue(Arrays.compareUnsigned(a, 2, 4, b, 1, 4) < 0);
      assertTrue(Arrays.compareUnsigned(b, 1, 4, a, 2, 4) > 0);

      try {
        Arrays.compareUnsigned(nullArray, 0, 1, b, 1, 2);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.compareUnsigned(a, 0, 1, nullArray, 1, 2);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.compareUnsigned(a, 2, 1, b, 1, 2);
        fail("Expected IllegalArgumentException");
      } catch (IllegalArgumentException expected) {
      }
      try {
        Arrays.compareUnsigned(a, -1, 1, b, 1, 2);
        fail("Expected ArrayIndexOutOfBoundsException");
      } catch (ArrayIndexOutOfBoundsException expected) {
      }
      try {
        Arrays.compareUnsigned(a, 0, 10, b, 1, 2);
        fail("Expected ArrayIndexOutOfBoundsException");
      } catch (ArrayIndexOutOfBoundsException expected) {
      }
    }

    private static void testCompareUnsignedLong() {
      long[] nullArray = System.currentTimeMillis() > 0 ? null : new long[0];

      assertTrue(Arrays.compareUnsigned(nullArray, nullArray) == 0);
      assertTrue(Arrays.compareUnsigned(nullArray, new long[0]) < 0);
      assertTrue(Arrays.compareUnsigned(new long[0], nullArray) > 0);

      long[] a = new long[] {1L, 2L, 3L};
      assertTrue(Arrays.compareUnsigned(a, a) == 0);
      assertTrue(Arrays.compareUnsigned(new long[0], new long[0]) == 0);
      assertTrue(Arrays.compareUnsigned(new long[] {1L, 2L, 3L}, new long[] {1L, 2L, 3L}) == 0);
      assertTrue(
          Arrays.compareUnsigned(
                  new long[] {Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE},
                  new long[] {Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE})
              == 0);

      assertTrue(Arrays.compareUnsigned(new long[] {1L, 2L}, new long[] {1L, 2L, 3L}) < 0);
      assertTrue(Arrays.compareUnsigned(new long[] {1L, 2L, 3L}, new long[] {1L, 2L}) > 0);

      assertTrue(Arrays.compareUnsigned(new long[] {0L}, new long[] {1L}) < 0);
      assertTrue(Arrays.compareUnsigned(new long[] {1L}, new long[] {Long.MAX_VALUE}) < 0);
      assertTrue(
          Arrays.compareUnsigned(new long[] {Long.MAX_VALUE}, new long[] {Long.MIN_VALUE}) < 0);
      assertTrue(Arrays.compareUnsigned(new long[] {Long.MIN_VALUE}, new long[] {-1L}) < 0);
      assertTrue(Arrays.compareUnsigned(new long[] {-1L}, new long[] {0L}) > 0);
      assertTrue(Arrays.compareUnsigned(new long[] {-1L}, new long[] {Long.MAX_VALUE}) > 0);
      assertTrue(Arrays.compareUnsigned(new long[] {-1L}, new long[] {Long.MIN_VALUE}) > 0);
      assertTrue(Arrays.compareUnsigned(new long[] {1L, 2L, -1L}, new long[] {1L, 2L, 0L}) > 0);
    }

    private static void testCompareUnsignedLongRange() {
      long[] nullArray = System.currentTimeMillis() > 0 ? null : new long[0];
      long[] a = new long[] {-3L, -2L, -1L, 0L, 1L, 2L, 3L};
      long[] b = new long[] {Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE};

      assertTrue(Arrays.compareUnsigned(new long[] {}, 0, 0, new long[] {}, 0, 0) == 0);
      assertTrue(Arrays.compareUnsigned(a, 0, 1, a, 0, 1) == 0);
      assertTrue(Arrays.compareUnsigned(a, 2, 5, b, 1, 4) == 0);
      assertTrue(Arrays.compareUnsigned(a, 1, 1, b, 2, 2) == 0);

      assertTrue(Arrays.compareUnsigned(a, 2, 3, b, 2, 3) > 0);
      assertTrue(Arrays.compareUnsigned(b, 2, 3, a, 2, 3) < 0);
      assertTrue(Arrays.compareUnsigned(a, 2, 4, b, 1, 4) < 0);
      assertTrue(Arrays.compareUnsigned(b, 1, 4, a, 2, 4) > 0);

      try {
        Arrays.compareUnsigned(nullArray, 0, 1, b, 1, 2);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.compareUnsigned(a, 0, 1, nullArray, 1, 2);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.compareUnsigned(a, 2, 1, b, 1, 2);
        fail("Expected IllegalArgumentException");
      } catch (IllegalArgumentException expected) {
      }
      try {
        Arrays.compareUnsigned(a, -1, 1, b, 1, 2);
        fail("Expected ArrayIndexOutOfBoundsException");
      } catch (ArrayIndexOutOfBoundsException expected) {
      }
      try {
        Arrays.compareUnsigned(a, 0, 10, b, 1, 2);
        fail("Expected ArrayIndexOutOfBoundsException");
      } catch (ArrayIndexOutOfBoundsException expected) {
      }
    }
  }
}
