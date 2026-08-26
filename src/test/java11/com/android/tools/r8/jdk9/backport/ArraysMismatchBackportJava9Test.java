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
import java.util.Comparator;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class ArraysMismatchBackportJava9Test extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return TestBase.getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public ArraysMismatchBackportJava9Test(TestParameters parameters) {
    super(parameters, Arrays.class, Main.class);

    // Arrays.mismatch methods were added in Android T (API 33).
    registerTarget(AndroidApiLevel.T, 202);
  }

  public static class Main extends MiniAssert {

    public static void main(String[] args) {
      testMismatchBoolean();
      testMismatchBooleanRange();
      testMismatchByte();
      testMismatchByteRange();
      testMismatchChar();
      testMismatchCharRange();
      testMismatchShort();
      testMismatchShortRange();
      testMismatchInt();
      testMismatchIntRange();
      testMismatchLong();
      testMismatchLongRange();
      testMismatchFloat();
      testMismatchFloatRange();
      testMismatchDouble();
      testMismatchDoubleRange();
      testMismatchObject();
      testMismatchObjectRange();
      testMismatchComparator();
      testMismatchComparatorRange();
    }

    private static void testMismatchBoolean() {
      boolean[] a = new boolean[] {true, false, true};
      boolean[] b = new boolean[] {true, false, true};
      boolean[] c = new boolean[] {true, true, true};
      boolean[] d = new boolean[] {true, false};
      boolean[] empty = new boolean[0];
      boolean[] nullArray = System.currentTimeMillis() > 0 ? null : new boolean[0];

      assertEquals(-1, Arrays.mismatch(a, a));
      assertEquals(-1, Arrays.mismatch(empty, empty));
      assertEquals(-1, Arrays.mismatch(a, b));
      assertEquals(1, Arrays.mismatch(a, c));
      assertEquals(2, Arrays.mismatch(a, d));
      assertEquals(2, Arrays.mismatch(d, a));
      assertEquals(0, Arrays.mismatch(new boolean[] {true}, new boolean[] {false}));

      try {
        Arrays.mismatch(nullArray, b);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, nullArray);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
    }

    private static void testMismatchBooleanRange() {
      boolean[] a = new boolean[] {false, true, false, true, false};
      boolean[] b = new boolean[] {true, true, false, true, true};
      boolean[] nullArray = System.currentTimeMillis() > 0 ? null : new boolean[0];

      assertEquals(-1, Arrays.mismatch(a, 0, 0, b, 0, 0));
      assertEquals(-1, Arrays.mismatch(a, 1, 4, b, 1, 4));
      assertEquals(-1, Arrays.mismatch(a, 0, 5, a, 0, 5));
      assertEquals(0, Arrays.mismatch(a, 0, 3, b, 0, 3));
      assertEquals(2, Arrays.mismatch(a, 1, 3, b, 1, 4));
      assertEquals(2, Arrays.mismatch(b, 1, 4, a, 1, 3));

      try {
        Arrays.mismatch(nullArray, 0, 1, b, 0, 1);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, 0, 1, nullArray, 0, 1);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, 2, 1, b, 0, 1);
        fail("Expected IllegalArgumentException");
      } catch (IllegalArgumentException expected) {
      }
      try {
        Arrays.mismatch(a, -1, 1, b, 0, 1);
        fail("Expected ArrayIndexOutOfBoundsException");
      } catch (ArrayIndexOutOfBoundsException expected) {
      }
      try {
        Arrays.mismatch(a, 0, 10, b, 0, 1);
        fail("Expected ArrayIndexOutOfBoundsException");
      } catch (ArrayIndexOutOfBoundsException expected) {
      }
    }

    private static void testMismatchByte() {
      byte[] a = new byte[] {1, 2, 3};
      byte[] b = new byte[] {1, 2, 3};
      byte[] c = new byte[] {1, 4, 3};
      byte[] d = new byte[] {1, 2};
      byte[] empty = new byte[0];
      byte[] nullArray = System.currentTimeMillis() > 0 ? null : new byte[0];

      assertEquals(-1, Arrays.mismatch(a, a));
      assertEquals(-1, Arrays.mismatch(empty, empty));
      assertEquals(-1, Arrays.mismatch(a, b));
      assertEquals(1, Arrays.mismatch(a, c));
      assertEquals(2, Arrays.mismatch(a, d));
      assertEquals(2, Arrays.mismatch(d, a));
      assertEquals(0, Arrays.mismatch(new byte[] {1}, new byte[] {2}));

      try {
        Arrays.mismatch(nullArray, b);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, nullArray);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
    }

    private static void testMismatchByteRange() {
      byte[] a = new byte[] {0, 1, 2, 3, 0};
      byte[] b = new byte[] {9, 1, 2, 3, 9};
      byte[] nullArray = System.currentTimeMillis() > 0 ? null : new byte[0];

      assertEquals(-1, Arrays.mismatch(a, 0, 0, b, 0, 0));
      assertEquals(-1, Arrays.mismatch(a, 1, 4, b, 1, 4));
      assertEquals(-1, Arrays.mismatch(a, 0, 5, a, 0, 5));
      assertEquals(0, Arrays.mismatch(a, 0, 3, b, 0, 3));
      assertEquals(2, Arrays.mismatch(a, 1, 3, b, 1, 4));
      assertEquals(2, Arrays.mismatch(b, 1, 4, a, 1, 3));

      try {
        Arrays.mismatch(nullArray, 0, 1, b, 0, 1);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, 0, 1, nullArray, 0, 1);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, 2, 1, b, 0, 1);
        fail("Expected IllegalArgumentException");
      } catch (IllegalArgumentException expected) {
      }
      try {
        Arrays.mismatch(a, -1, 1, b, 0, 1);
        fail("Expected ArrayIndexOutOfBoundsException");
      } catch (ArrayIndexOutOfBoundsException expected) {
      }
      try {
        Arrays.mismatch(a, 0, 10, b, 0, 1);
        fail("Expected ArrayIndexOutOfBoundsException");
      } catch (ArrayIndexOutOfBoundsException expected) {
      }
    }

    private static void testMismatchChar() {
      char[] a = new char[] {'a', 'b', 'c'};
      char[] b = new char[] {'a', 'b', 'c'};
      char[] c = new char[] {'a', 'x', 'c'};
      char[] d = new char[] {'a', 'b'};
      char[] empty = new char[0];
      char[] nullArray = System.currentTimeMillis() > 0 ? null : new char[0];

      assertEquals(-1, Arrays.mismatch(a, a));
      assertEquals(-1, Arrays.mismatch(empty, empty));
      assertEquals(-1, Arrays.mismatch(a, b));
      assertEquals(1, Arrays.mismatch(a, c));
      assertEquals(2, Arrays.mismatch(a, d));
      assertEquals(2, Arrays.mismatch(d, a));
      assertEquals(0, Arrays.mismatch(new char[] {'a'}, new char[] {'z'}));

      try {
        Arrays.mismatch(nullArray, b);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, nullArray);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
    }

    private static void testMismatchCharRange() {
      char[] a = new char[] {'x', 'a', 'b', 'c', 'x'};
      char[] b = new char[] {'y', 'a', 'b', 'c', 'y'};
      char[] nullArray = System.currentTimeMillis() > 0 ? null : new char[0];

      assertEquals(-1, Arrays.mismatch(a, 0, 0, b, 0, 0));
      assertEquals(-1, Arrays.mismatch(a, 1, 4, b, 1, 4));
      assertEquals(-1, Arrays.mismatch(a, 0, 5, a, 0, 5));
      assertEquals(0, Arrays.mismatch(a, 0, 3, b, 0, 3));
      assertEquals(2, Arrays.mismatch(a, 1, 3, b, 1, 4));
      assertEquals(2, Arrays.mismatch(b, 1, 4, a, 1, 3));

      try {
        Arrays.mismatch(nullArray, 0, 1, b, 0, 1);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, 0, 1, nullArray, 0, 1);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, 2, 1, b, 0, 1);
        fail("Expected IllegalArgumentException");
      } catch (IllegalArgumentException expected) {
      }
      try {
        Arrays.mismatch(a, -1, 1, b, 0, 1);
        fail("Expected ArrayIndexOutOfBoundsException");
      } catch (ArrayIndexOutOfBoundsException expected) {
      }
      try {
        Arrays.mismatch(a, 0, 10, b, 0, 1);
        fail("Expected ArrayIndexOutOfBoundsException");
      } catch (ArrayIndexOutOfBoundsException expected) {
      }
    }

    private static void testMismatchShort() {
      short[] a = new short[] {1, 2, 3};
      short[] b = new short[] {1, 2, 3};
      short[] c = new short[] {1, 4, 3};
      short[] d = new short[] {1, 2};
      short[] empty = new short[0];
      short[] nullArray = System.currentTimeMillis() > 0 ? null : new short[0];

      assertEquals(-1, Arrays.mismatch(a, a));
      assertEquals(-1, Arrays.mismatch(empty, empty));
      assertEquals(-1, Arrays.mismatch(a, b));
      assertEquals(1, Arrays.mismatch(a, c));
      assertEquals(2, Arrays.mismatch(a, d));
      assertEquals(2, Arrays.mismatch(d, a));
      assertEquals(0, Arrays.mismatch(new short[] {1}, new short[] {2}));

      try {
        Arrays.mismatch(nullArray, b);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, nullArray);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
    }

    private static void testMismatchShortRange() {
      short[] a = new short[] {0, 1, 2, 3, 0};
      short[] b = new short[] {9, 1, 2, 3, 9};
      short[] nullArray = System.currentTimeMillis() > 0 ? null : new short[0];

      assertEquals(-1, Arrays.mismatch(a, 0, 0, b, 0, 0));
      assertEquals(-1, Arrays.mismatch(a, 1, 4, b, 1, 4));
      assertEquals(-1, Arrays.mismatch(a, 0, 5, a, 0, 5));
      assertEquals(0, Arrays.mismatch(a, 0, 3, b, 0, 3));
      assertEquals(2, Arrays.mismatch(a, 1, 3, b, 1, 4));
      assertEquals(2, Arrays.mismatch(b, 1, 4, a, 1, 3));

      try {
        Arrays.mismatch(nullArray, 0, 1, b, 0, 1);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, 0, 1, nullArray, 0, 1);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, 2, 1, b, 0, 1);
        fail("Expected IllegalArgumentException");
      } catch (IllegalArgumentException expected) {
      }
      try {
        Arrays.mismatch(a, -1, 1, b, 0, 1);
        fail("Expected ArrayIndexOutOfBoundsException");
      } catch (ArrayIndexOutOfBoundsException expected) {
      }
      try {
        Arrays.mismatch(a, 0, 10, b, 0, 1);
        fail("Expected ArrayIndexOutOfBoundsException");
      } catch (ArrayIndexOutOfBoundsException expected) {
      }
    }

    private static void testMismatchInt() {
      int[] a = new int[] {1, 2, 3};
      int[] b = new int[] {1, 2, 3};
      int[] c = new int[] {1, 4, 3};
      int[] d = new int[] {1, 2};
      int[] empty = new int[0];
      int[] nullArray = System.currentTimeMillis() > 0 ? null : new int[0];

      assertEquals(-1, Arrays.mismatch(a, a));
      assertEquals(-1, Arrays.mismatch(empty, empty));
      assertEquals(-1, Arrays.mismatch(a, b));
      assertEquals(1, Arrays.mismatch(a, c));
      assertEquals(2, Arrays.mismatch(a, d));
      assertEquals(2, Arrays.mismatch(d, a));
      assertEquals(0, Arrays.mismatch(new int[] {1}, new int[] {2}));

      try {
        Arrays.mismatch(nullArray, b);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, nullArray);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
    }

    private static void testMismatchIntRange() {
      int[] a = new int[] {0, 1, 2, 3, 0};
      int[] b = new int[] {9, 1, 2, 3, 9};
      int[] nullArray = System.currentTimeMillis() > 0 ? null : new int[0];

      assertEquals(-1, Arrays.mismatch(a, 0, 0, b, 0, 0));
      assertEquals(-1, Arrays.mismatch(a, 1, 4, b, 1, 4));
      assertEquals(-1, Arrays.mismatch(a, 0, 5, a, 0, 5));
      assertEquals(0, Arrays.mismatch(a, 0, 3, b, 0, 3));
      assertEquals(2, Arrays.mismatch(a, 1, 3, b, 1, 4));
      assertEquals(2, Arrays.mismatch(b, 1, 4, a, 1, 3));

      try {
        Arrays.mismatch(nullArray, 0, 1, b, 0, 1);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, 0, 1, nullArray, 0, 1);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, 2, 1, b, 0, 1);
        fail("Expected IllegalArgumentException");
      } catch (IllegalArgumentException expected) {
      }
      try {
        Arrays.mismatch(a, -1, 1, b, 0, 1);
        fail("Expected ArrayIndexOutOfBoundsException");
      } catch (ArrayIndexOutOfBoundsException expected) {
      }
      try {
        Arrays.mismatch(a, 0, 10, b, 0, 1);
        fail("Expected ArrayIndexOutOfBoundsException");
      } catch (ArrayIndexOutOfBoundsException expected) {
      }
    }

    private static void testMismatchLong() {
      long[] a = new long[] {1L, 2L, 3L};
      long[] b = new long[] {1L, 2L, 3L};
      long[] c = new long[] {1L, 4L, 3L};
      long[] d = new long[] {1L, 2L};
      long[] empty = new long[0];
      long[] nullArray = System.currentTimeMillis() > 0 ? null : new long[0];

      assertEquals(-1, Arrays.mismatch(a, a));
      assertEquals(-1, Arrays.mismatch(empty, empty));
      assertEquals(-1, Arrays.mismatch(a, b));
      assertEquals(1, Arrays.mismatch(a, c));
      assertEquals(2, Arrays.mismatch(a, d));
      assertEquals(2, Arrays.mismatch(d, a));
      assertEquals(0, Arrays.mismatch(new long[] {1L}, new long[] {2L}));

      try {
        Arrays.mismatch(nullArray, b);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, nullArray);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
    }

    private static void testMismatchLongRange() {
      long[] a = new long[] {0L, 1L, 2L, 3L, 0L};
      long[] b = new long[] {9L, 1L, 2L, 3L, 9L};
      long[] nullArray = System.currentTimeMillis() > 0 ? null : new long[0];

      assertEquals(-1, Arrays.mismatch(a, 0, 0, b, 0, 0));
      assertEquals(-1, Arrays.mismatch(a, 1, 4, b, 1, 4));
      assertEquals(-1, Arrays.mismatch(a, 0, 5, a, 0, 5));
      assertEquals(0, Arrays.mismatch(a, 0, 3, b, 0, 3));
      assertEquals(2, Arrays.mismatch(a, 1, 3, b, 1, 4));
      assertEquals(2, Arrays.mismatch(b, 1, 4, a, 1, 3));

      try {
        Arrays.mismatch(nullArray, 0, 1, b, 0, 1);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, 0, 1, nullArray, 0, 1);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, 2, 1, b, 0, 1);
        fail("Expected IllegalArgumentException");
      } catch (IllegalArgumentException expected) {
      }
      try {
        Arrays.mismatch(a, -1, 1, b, 0, 1);
        fail("Expected ArrayIndexOutOfBoundsException");
      } catch (ArrayIndexOutOfBoundsException expected) {
      }
      try {
        Arrays.mismatch(a, 0, 10, b, 0, 1);
        fail("Expected ArrayIndexOutOfBoundsException");
      } catch (ArrayIndexOutOfBoundsException expected) {
      }
    }

    private static void testMismatchFloat() {
      float[] a = new float[] {1.0f, Float.NaN, 3.0f, 0.0f};
      float[] b = new float[] {1.0f, Float.NaN, 3.0f, 0.0f};
      float[] c = new float[] {1.0f, Float.NaN, 3.0f, -0.0f};
      float[] d = new float[] {1.0f, Float.NaN};
      float[] empty = new float[0];
      float[] nullArray = System.currentTimeMillis() > 0 ? null : new float[0];

      assertEquals(-1, Arrays.mismatch(a, a));
      assertEquals(-1, Arrays.mismatch(empty, empty));
      assertEquals(-1, Arrays.mismatch(a, b));
      assertEquals(3, Arrays.mismatch(a, c)); // 0.0f != -0.0f in bit comparison
      assertEquals(2, Arrays.mismatch(a, d));
      assertEquals(2, Arrays.mismatch(d, a));
      assertEquals(-1, Arrays.mismatch(new float[] {Float.NaN}, new float[] {Float.NaN}));

      try {
        Arrays.mismatch(nullArray, b);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, nullArray);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
    }

    private static void testMismatchFloatRange() {
      float[] a = new float[] {0.0f, 1.0f, Float.NaN, 3.0f, 0.0f};
      float[] b = new float[] {9.0f, 1.0f, Float.NaN, 3.0f, 9.0f};
      float[] nullArray = System.currentTimeMillis() > 0 ? null : new float[0];

      assertEquals(-1, Arrays.mismatch(a, 0, 0, b, 0, 0));
      assertEquals(-1, Arrays.mismatch(a, 1, 4, b, 1, 4));
      assertEquals(-1, Arrays.mismatch(a, 0, 5, a, 0, 5));
      assertEquals(0, Arrays.mismatch(a, 0, 3, b, 0, 3));
      assertEquals(2, Arrays.mismatch(a, 1, 3, b, 1, 4));
      assertEquals(2, Arrays.mismatch(b, 1, 4, a, 1, 3));

      try {
        Arrays.mismatch(nullArray, 0, 1, b, 0, 1);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, 0, 1, nullArray, 0, 1);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, 2, 1, b, 0, 1);
        fail("Expected IllegalArgumentException");
      } catch (IllegalArgumentException expected) {
      }
      try {
        Arrays.mismatch(a, -1, 1, b, 0, 1);
        fail("Expected ArrayIndexOutOfBoundsException");
      } catch (ArrayIndexOutOfBoundsException expected) {
      }
      try {
        Arrays.mismatch(a, 0, 10, b, 0, 1);
        fail("Expected ArrayIndexOutOfBoundsException");
      } catch (ArrayIndexOutOfBoundsException expected) {
      }
    }

    private static void testMismatchDouble() {
      double[] a = new double[] {1.0, Double.NaN, 3.0, 0.0};
      double[] b = new double[] {1.0, Double.NaN, 3.0, 0.0};
      double[] c = new double[] {1.0, Double.NaN, 3.0, -0.0};
      double[] d = new double[] {1.0, Double.NaN};
      double[] empty = new double[0];
      double[] nullArray = System.currentTimeMillis() > 0 ? null : new double[0];

      assertEquals(-1, Arrays.mismatch(a, a));
      assertEquals(-1, Arrays.mismatch(empty, empty));
      assertEquals(-1, Arrays.mismatch(a, b));
      assertEquals(3, Arrays.mismatch(a, c)); // 0.0 != -0.0 in bit comparison
      assertEquals(2, Arrays.mismatch(a, d));
      assertEquals(2, Arrays.mismatch(d, a));
      assertEquals(-1, Arrays.mismatch(new double[] {Double.NaN}, new double[] {Double.NaN}));

      try {
        Arrays.mismatch(nullArray, b);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, nullArray);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
    }

    private static void testMismatchDoubleRange() {
      double[] a = new double[] {0.0, 1.0, Double.NaN, 3.0, 0.0};
      double[] b = new double[] {9.0, 1.0, Double.NaN, 3.0, 9.0};
      double[] nullArray = System.currentTimeMillis() > 0 ? null : new double[0];

      assertEquals(-1, Arrays.mismatch(a, 0, 0, b, 0, 0));
      assertEquals(-1, Arrays.mismatch(a, 1, 4, b, 1, 4));
      assertEquals(-1, Arrays.mismatch(a, 0, 5, a, 0, 5));
      assertEquals(0, Arrays.mismatch(a, 0, 3, b, 0, 3));
      assertEquals(2, Arrays.mismatch(a, 1, 3, b, 1, 4));
      assertEquals(2, Arrays.mismatch(b, 1, 4, a, 1, 3));

      try {
        Arrays.mismatch(nullArray, 0, 1, b, 0, 1);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, 0, 1, nullArray, 0, 1);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, 2, 1, b, 0, 1);
        fail("Expected IllegalArgumentException");
      } catch (IllegalArgumentException expected) {
      }
      try {
        Arrays.mismatch(a, -1, 1, b, 0, 1);
        fail("Expected ArrayIndexOutOfBoundsException");
      } catch (ArrayIndexOutOfBoundsException expected) {
      }
      try {
        Arrays.mismatch(a, 0, 10, b, 0, 1);
        fail("Expected ArrayIndexOutOfBoundsException");
      } catch (ArrayIndexOutOfBoundsException expected) {
      }
    }

    private static void testMismatchObject() {
      Object[] a = new Object[] {"a", null, "c"};
      Object[] b = new Object[] {"a", null, "c"};
      Object[] c = new Object[] {"a", "b", "c"};
      Object[] d = new Object[] {"a", null};
      Object[] empty = new Object[0];
      Object[] nullArray = System.currentTimeMillis() > 0 ? null : new Object[0];

      assertEquals(-1, Arrays.mismatch(a, a));
      assertEquals(-1, Arrays.mismatch(empty, empty));
      assertEquals(-1, Arrays.mismatch(a, b));
      assertEquals(1, Arrays.mismatch(a, c));
      assertEquals(2, Arrays.mismatch(a, d));
      assertEquals(2, Arrays.mismatch(d, a));
      assertEquals(0, Arrays.mismatch(new Object[] {"x"}, new Object[] {"y"}));

      try {
        Arrays.mismatch(nullArray, b);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, nullArray);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
    }

    private static void testMismatchObjectRange() {
      Object[] a = new Object[] {"x", "a", null, "c", "x"};
      Object[] b = new Object[] {"y", "a", null, "c", "y"};
      Object[] nullArray = System.currentTimeMillis() > 0 ? null : new Object[0];

      assertEquals(-1, Arrays.mismatch(a, 0, 0, b, 0, 0));
      assertEquals(-1, Arrays.mismatch(a, 1, 4, b, 1, 4));
      assertEquals(-1, Arrays.mismatch(a, 0, 5, a, 0, 5));
      assertEquals(0, Arrays.mismatch(a, 0, 3, b, 0, 3));
      assertEquals(2, Arrays.mismatch(a, 1, 3, b, 1, 4));
      assertEquals(2, Arrays.mismatch(b, 1, 4, a, 1, 3));

      try {
        Arrays.mismatch(nullArray, 0, 1, b, 0, 1);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, 0, 1, nullArray, 0, 1);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, 2, 1, b, 0, 1);
        fail("Expected IllegalArgumentException");
      } catch (IllegalArgumentException expected) {
      }
      try {
        Arrays.mismatch(a, -1, 1, b, 0, 1);
        fail("Expected ArrayIndexOutOfBoundsException");
      } catch (ArrayIndexOutOfBoundsException expected) {
      }
      try {
        Arrays.mismatch(a, 0, 10, b, 0, 1);
        fail("Expected ArrayIndexOutOfBoundsException");
      } catch (ArrayIndexOutOfBoundsException expected) {
      }
    }

    private static void testMismatchComparator() {
      String[] a = new String[] {"a", "B", "c"};
      String[] b = new String[] {"A", "b", "C"};
      String[] c = new String[] {"a", "b", "x"};
      String[] d = new String[] {"a", "b"};
      String[] empty = new String[0];
      String[] nullArray = System.currentTimeMillis() > 0 ? null : new String[0];
      Comparator<String> caseInsensitive = String.CASE_INSENSITIVE_ORDER;

      assertEquals(-1, Arrays.mismatch(a, a, caseInsensitive));
      assertEquals(-1, Arrays.mismatch(empty, empty, caseInsensitive));
      assertEquals(-1, Arrays.mismatch(a, b, caseInsensitive));
      assertEquals(2, Arrays.mismatch(a, c, caseInsensitive));
      assertEquals(2, Arrays.mismatch(a, d, caseInsensitive));
      assertEquals(2, Arrays.mismatch(d, a, caseInsensitive));
      assertEquals(0, Arrays.mismatch(new String[] {"a"}, new String[] {"z"}, caseInsensitive));

      try {
        Arrays.mismatch(nullArray, b, caseInsensitive);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, nullArray, caseInsensitive);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, b, null);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
    }

    private static void testMismatchComparatorRange() {
      String[] a = new String[] {"x", "a", "B", "c", "x"};
      String[] b = new String[] {"y", "A", "b", "C", "y"};
      String[] nullArray = System.currentTimeMillis() > 0 ? null : new String[0];
      Comparator<String> caseInsensitive = String.CASE_INSENSITIVE_ORDER;

      assertEquals(-1, Arrays.mismatch(a, 0, 0, b, 0, 0, caseInsensitive));
      assertEquals(-1, Arrays.mismatch(a, 1, 4, b, 1, 4, caseInsensitive));
      assertEquals(-1, Arrays.mismatch(a, 0, 5, a, 0, 5, caseInsensitive));
      assertEquals(0, Arrays.mismatch(a, 0, 3, b, 0, 3, caseInsensitive));
      assertEquals(2, Arrays.mismatch(a, 1, 3, b, 1, 4, caseInsensitive));
      assertEquals(2, Arrays.mismatch(b, 1, 4, a, 1, 3, caseInsensitive));

      try {
        Arrays.mismatch(nullArray, 0, 1, b, 0, 1, caseInsensitive);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, 0, 1, nullArray, 0, 1, caseInsensitive);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, 0, 1, b, 0, 1, null);
        fail("Expected NullPointerException");
      } catch (NullPointerException expected) {
      }
      try {
        Arrays.mismatch(a, 2, 1, b, 0, 1, caseInsensitive);
        fail("Expected IllegalArgumentException");
      } catch (IllegalArgumentException expected) {
      }
      try {
        Arrays.mismatch(a, -1, 1, b, 0, 1, caseInsensitive);
        fail("Expected ArrayIndexOutOfBoundsException");
      } catch (ArrayIndexOutOfBoundsException expected) {
      }
      try {
        Arrays.mismatch(a, 0, 10, b, 0, 1, caseInsensitive);
        fail("Expected ArrayIndexOutOfBoundsException");
      } catch (ArrayIndexOutOfBoundsException expected) {
      }
    }
  }
}
