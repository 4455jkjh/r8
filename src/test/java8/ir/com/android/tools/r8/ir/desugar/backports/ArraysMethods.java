// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.ir.desugar.backports.BackportMethodsStub.ArraysStub;
import com.android.tools.r8.ir.desugar.backports.BackportMethodsStub.ByteStub;
import com.android.tools.r8.ir.desugar.backports.BackportMethodsStub.ShortStub;
import java.util.Comparator;
import java.util.Objects;

public final class ArraysMethods {

  public static void checkValidRange(int arrayLength, int fromIndex, int toIndex) {
    if (fromIndex > toIndex) {
      throw new IllegalArgumentException("fromIndex(" + fromIndex + ") > toIndex(" + toIndex + ")");
    }
    if (fromIndex < 0) {
      throw new ArrayIndexOutOfBoundsException(fromIndex);
    }
    if (toIndex > arrayLength) {
      throw new ArrayIndexOutOfBoundsException(toIndex);
    }
  }

  public static boolean equalsInt(int[] a, int[] b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    if (a.length != b.length) {
      return false;
    }
    for (int i = 0; i < a.length; i++) {
      if (a[i] != b[i]) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsIntRange(int[] a, int aFrom, int aTo, int[] b, int bFrom, int bTo) {
    checkValidRange(a.length, aFrom, aTo);
    checkValidRange(b.length, bFrom, bTo);

    int aLength = aTo - aFrom;
    int bLength = bTo - bFrom;
    if (aLength != bLength) {
      return false;
    }
    for (int i = 0; i < aLength; i++) {
      if (a[aFrom++] != b[bFrom++]) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsLong(long[] a, long[] b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    if (a.length != b.length) {
      return false;
    }
    for (int i = 0; i < a.length; i++) {
      if (a[i] != b[i]) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsLongRange(
      long[] a, int aFrom, int aTo, long[] b, int bFrom, int bTo) {
    checkValidRange(a.length, aFrom, aTo);
    checkValidRange(b.length, bFrom, bTo);

    int aLength = aTo - aFrom;
    int bLength = bTo - bFrom;
    if (aLength != bLength) {
      return false;
    }
    for (int i = 0; i < aLength; i++) {
      if (a[aFrom++] != b[bFrom++]) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsFloat(float[] a, float[] b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    if (a.length != b.length) {
      return false;
    }
    for (int i = 0; i < a.length; i++) {
      if (Float.floatToIntBits(a[i]) != Float.floatToIntBits(b[i])) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsFloatRange(
      float[] a, int aFrom, int aTo, float[] b, int bFrom, int bTo) {
    checkValidRange(a.length, aFrom, aTo);
    checkValidRange(b.length, bFrom, bTo);

    int aLength = aTo - aFrom;
    int bLength = bTo - bFrom;
    if (aLength != bLength) {
      return false;
    }
    for (int i = 0; i < aLength; i++) {
      if (Float.floatToIntBits(a[aFrom++]) != Float.floatToIntBits(b[bFrom++])) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsDouble(double[] a, double[] b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    if (a.length != b.length) {
      return false;
    }
    for (int i = 0; i < a.length; i++) {
      if (Double.doubleToLongBits(a[i]) != Double.doubleToLongBits(b[i])) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsDoubleRange(
      double[] a, int aFrom, int aTo, double[] b, int bFrom, int bTo) {
    checkValidRange(a.length, aFrom, aTo);
    checkValidRange(b.length, bFrom, bTo);

    int aLength = aTo - aFrom;
    int bLength = bTo - bFrom;
    if (aLength != bLength) {
      return false;
    }
    for (int i = 0; i < aLength; i++) {
      if (Double.doubleToLongBits(a[aFrom++]) != Double.doubleToLongBits(b[bFrom++])) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsShort(short[] a, short[] b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    if (a.length != b.length) {
      return false;
    }
    for (int i = 0; i < a.length; i++) {
      if (a[i] != b[i]) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsShortRange(
      short[] a, int aFrom, int aTo, short[] b, int bFrom, int bTo) {
    checkValidRange(a.length, aFrom, aTo);
    checkValidRange(b.length, bFrom, bTo);

    int aLength = aTo - aFrom;
    int bLength = bTo - bFrom;
    if (aLength != bLength) {
      return false;
    }
    for (int i = 0; i < aLength; i++) {
      if (a[aFrom++] != b[bFrom++]) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsByte(byte[] a, byte[] b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    if (a.length != b.length) {
      return false;
    }
    for (int i = 0; i < a.length; i++) {
      if (a[i] != b[i]) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsByteRange(
      byte[] a, int aFrom, int aTo, byte[] b, int bFrom, int bTo) {
    checkValidRange(a.length, aFrom, aTo);
    checkValidRange(b.length, bFrom, bTo);

    int aLength = aTo - aFrom;
    int bLength = bTo - bFrom;
    if (aLength != bLength) {
      return false;
    }
    for (int i = 0; i < aLength; i++) {
      if (a[aFrom++] != b[bFrom++]) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsChar(char[] a, char[] b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    if (a.length != b.length) {
      return false;
    }
    for (int i = 0; i < a.length; i++) {
      if (a[i] != b[i]) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsCharRange(
      char[] a, int aFrom, int aTo, char[] b, int bFrom, int bTo) {
    checkValidRange(a.length, aFrom, aTo);
    checkValidRange(b.length, bFrom, bTo);

    int aLength = aTo - aFrom;
    int bLength = bTo - bFrom;
    if (aLength != bLength) {
      return false;
    }
    for (int i = 0; i < aLength; i++) {
      if (a[aFrom++] != b[bFrom++]) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsBoolean(boolean[] a, boolean[] b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    if (a.length != b.length) {
      return false;
    }
    for (int i = 0; i < a.length; i++) {
      if (a[i] != b[i]) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsBooleanRange(
      boolean[] a, int aFrom, int aTo, boolean[] b, int bFrom, int bTo) {
    checkValidRange(a.length, aFrom, aTo);
    checkValidRange(b.length, bFrom, bTo);

    int aLength = aTo - aFrom;
    int bLength = bTo - bFrom;
    if (aLength != bLength) {
      return false;
    }
    for (int i = 0; i < aLength; i++) {
      if (a[aFrom++] != b[bFrom++]) {
        return false;
      }
    }
    return true;
  }

  public static int compareUnsignedByte(byte[] a, byte[] b) {
    if (a == b) {
      return 0;
    }
    if (a == null) {
      return -1;
    }
    if (b == null) {
      return 1;
    }
    int i = ArraysStub.mismatch(a, b);
    if (i >= 0 && i < Math.min(a.length, b.length)) {
      return ByteStub.compareUnsigned(a[i], b[i]);
    }
    return a.length - b.length;
  }

  public static int compareUnsignedByteRange(
      byte[] a, int aFromIndex, int aToIndex, byte[] b, int bFromIndex, int bToIndex) {
    int i = ArraysStub.mismatch(a, aFromIndex, aToIndex, b, bFromIndex, bToIndex);
    if (i >= 0 && i < Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex)) {
      return ByteStub.compareUnsigned(a[aFromIndex + i], b[bFromIndex + i]);
    }
    return (aToIndex - aFromIndex) - (bToIndex - bFromIndex);
  }

  public static int compareUnsignedShort(short[] a, short[] b) {
    if (a == b) {
      return 0;
    }
    if (a == null) {
      return -1;
    }
    if (b == null) {
      return 1;
    }
    int i = ArraysStub.mismatch(a, b);
    if (i >= 0 && i < Math.min(a.length, b.length)) {
      return ShortStub.compareUnsigned(a[i], b[i]);
    }
    return a.length - b.length;
  }

  public static int compareUnsignedShortRange(
      short[] a, int aFromIndex, int aToIndex, short[] b, int bFromIndex, int bToIndex) {
    int i = ArraysStub.mismatch(a, aFromIndex, aToIndex, b, bFromIndex, bToIndex);
    if (i >= 0 && i < Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex)) {
      return ShortStub.compareUnsigned(a[aFromIndex + i], b[bFromIndex + i]);
    }
    return (aToIndex - aFromIndex) - (bToIndex - bFromIndex);
  }

  public static int compareUnsignedInt(int[] a, int[] b) {
    if (a == b) {
      return 0;
    }
    if (a == null) {
      return -1;
    }
    if (b == null) {
      return 1;
    }
    int i = ArraysStub.mismatch(a, b);
    if (i >= 0 && i < Math.min(a.length, b.length)) {
      return Integer.compareUnsigned(a[i], b[i]);
    }
    return a.length - b.length;
  }

  public static int compareUnsignedIntRange(
      int[] a, int aFromIndex, int aToIndex, int[] b, int bFromIndex, int bToIndex) {
    int i = ArraysStub.mismatch(a, aFromIndex, aToIndex, b, bFromIndex, bToIndex);
    if (i >= 0 && i < Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex)) {
      return Integer.compareUnsigned(a[aFromIndex + i], b[bFromIndex + i]);
    }
    return (aToIndex - aFromIndex) - (bToIndex - bFromIndex);
  }

  public static int compareUnsignedLong(long[] a, long[] b) {
    if (a == b) {
      return 0;
    }
    if (a == null) {
      return -1;
    }
    if (b == null) {
      return 1;
    }
    int i = ArraysStub.mismatch(a, b);
    if (i >= 0 && i < Math.min(a.length, b.length)) {
      return Long.compareUnsigned(a[i], b[i]);
    }
    return a.length - b.length;
  }

  public static int compareUnsignedLongRange(
      long[] a, int aFromIndex, int aToIndex, long[] b, int bFromIndex, int bToIndex) {
    int i = ArraysStub.mismatch(a, aFromIndex, aToIndex, b, bFromIndex, bToIndex);
    if (i >= 0 && i < Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex)) {
      return Long.compareUnsigned(a[aFromIndex + i], b[bFromIndex + i]);
    }
    return (aToIndex - aFromIndex) - (bToIndex - bFromIndex);
  }

  public static int mismatchBoolean(boolean[] a, boolean[] b) {
    int length = Math.min(a.length, b.length);
    if (a == b) {
      return -1;
    }
    for (int i = 0; i < length; i++) {
      if (a[i] != b[i]) {
        return i;
      }
    }
    return a.length != b.length ? length : -1;
  }

  public static int mismatchBooleanRange(
      boolean[] a, int aFrom, int aTo, boolean[] b, int bFrom, int bTo) {
    checkValidRange(a.length, aFrom, aTo);
    checkValidRange(b.length, bFrom, bTo);
    int aLength = aTo - aFrom;
    int bLength = bTo - bFrom;
    int length = Math.min(aLength, bLength);
    for (int i = 0; i < length; i++) {
      if (a[aFrom + i] != b[bFrom + i]) {
        return i;
      }
    }
    return aLength != bLength ? length : -1;
  }

  public static int mismatchByte(byte[] a, byte[] b) {
    int length = Math.min(a.length, b.length);
    if (a == b) {
      return -1;
    }
    for (int i = 0; i < length; i++) {
      if (a[i] != b[i]) {
        return i;
      }
    }
    return a.length != b.length ? length : -1;
  }

  public static int mismatchByteRange(byte[] a, int aFrom, int aTo, byte[] b, int bFrom, int bTo) {
    checkValidRange(a.length, aFrom, aTo);
    checkValidRange(b.length, bFrom, bTo);
    int aLength = aTo - aFrom;
    int bLength = bTo - bFrom;
    int length = Math.min(aLength, bLength);
    for (int i = 0; i < length; i++) {
      if (a[aFrom + i] != b[bFrom + i]) {
        return i;
      }
    }
    return aLength != bLength ? length : -1;
  }

  public static int mismatchChar(char[] a, char[] b) {
    int length = Math.min(a.length, b.length);
    if (a == b) {
      return -1;
    }
    for (int i = 0; i < length; i++) {
      if (a[i] != b[i]) {
        return i;
      }
    }
    return a.length != b.length ? length : -1;
  }

  public static int mismatchCharRange(char[] a, int aFrom, int aTo, char[] b, int bFrom, int bTo) {
    checkValidRange(a.length, aFrom, aTo);
    checkValidRange(b.length, bFrom, bTo);
    int aLength = aTo - aFrom;
    int bLength = bTo - bFrom;
    int length = Math.min(aLength, bLength);
    for (int i = 0; i < length; i++) {
      if (a[aFrom + i] != b[bFrom + i]) {
        return i;
      }
    }
    return aLength != bLength ? length : -1;
  }

  public static int mismatchShort(short[] a, short[] b) {
    int length = Math.min(a.length, b.length);
    if (a == b) {
      return -1;
    }
    for (int i = 0; i < length; i++) {
      if (a[i] != b[i]) {
        return i;
      }
    }
    return a.length != b.length ? length : -1;
  }

  public static int mismatchShortRange(
      short[] a, int aFrom, int aTo, short[] b, int bFrom, int bTo) {
    checkValidRange(a.length, aFrom, aTo);
    checkValidRange(b.length, bFrom, bTo);
    int aLength = aTo - aFrom;
    int bLength = bTo - bFrom;
    int length = Math.min(aLength, bLength);
    for (int i = 0; i < length; i++) {
      if (a[aFrom + i] != b[bFrom + i]) {
        return i;
      }
    }
    return aLength != bLength ? length : -1;
  }

  public static int mismatchInt(int[] a, int[] b) {
    int length = Math.min(a.length, b.length);
    if (a == b) {
      return -1;
    }
    for (int i = 0; i < length; i++) {
      if (a[i] != b[i]) {
        return i;
      }
    }
    return a.length != b.length ? length : -1;
  }

  public static int mismatchIntRange(int[] a, int aFrom, int aTo, int[] b, int bFrom, int bTo) {
    checkValidRange(a.length, aFrom, aTo);
    checkValidRange(b.length, bFrom, bTo);
    int aLength = aTo - aFrom;
    int bLength = bTo - bFrom;
    int length = Math.min(aLength, bLength);
    for (int i = 0; i < length; i++) {
      if (a[aFrom + i] != b[bFrom + i]) {
        return i;
      }
    }
    return aLength != bLength ? length : -1;
  }

  public static int mismatchLong(long[] a, long[] b) {
    int length = Math.min(a.length, b.length);
    if (a == b) {
      return -1;
    }
    for (int i = 0; i < length; i++) {
      if (a[i] != b[i]) {
        return i;
      }
    }
    return a.length != b.length ? length : -1;
  }

  public static int mismatchLongRange(long[] a, int aFrom, int aTo, long[] b, int bFrom, int bTo) {
    checkValidRange(a.length, aFrom, aTo);
    checkValidRange(b.length, bFrom, bTo);
    int aLength = aTo - aFrom;
    int bLength = bTo - bFrom;
    int length = Math.min(aLength, bLength);
    for (int i = 0; i < length; i++) {
      if (a[aFrom + i] != b[bFrom + i]) {
        return i;
      }
    }
    return aLength != bLength ? length : -1;
  }

  public static int mismatchFloat(float[] a, float[] b) {
    int length = Math.min(a.length, b.length);
    if (a == b) {
      return -1;
    }
    for (int i = 0; i < length; i++) {
      if (Float.floatToIntBits(a[i]) != Float.floatToIntBits(b[i])) {
        return i;
      }
    }
    return a.length != b.length ? length : -1;
  }

  public static int mismatchFloatRange(
      float[] a, int aFrom, int aTo, float[] b, int bFrom, int bTo) {
    checkValidRange(a.length, aFrom, aTo);
    checkValidRange(b.length, bFrom, bTo);
    int aLength = aTo - aFrom;
    int bLength = bTo - bFrom;
    int length = Math.min(aLength, bLength);
    for (int i = 0; i < length; i++) {
      if (Float.floatToIntBits(a[aFrom + i]) != Float.floatToIntBits(b[bFrom + i])) {
        return i;
      }
    }
    return aLength != bLength ? length : -1;
  }

  public static int mismatchDouble(double[] a, double[] b) {
    int length = Math.min(a.length, b.length);
    if (a == b) {
      return -1;
    }
    for (int i = 0; i < length; i++) {
      if (Double.doubleToLongBits(a[i]) != Double.doubleToLongBits(b[i])) {
        return i;
      }
    }
    return a.length != b.length ? length : -1;
  }

  public static int mismatchDoubleRange(
      double[] a, int aFrom, int aTo, double[] b, int bFrom, int bTo) {
    checkValidRange(a.length, aFrom, aTo);
    checkValidRange(b.length, bFrom, bTo);
    int aLength = aTo - aFrom;
    int bLength = bTo - bFrom;
    int length = Math.min(aLength, bLength);
    for (int i = 0; i < length; i++) {
      if (Double.doubleToLongBits(a[aFrom + i]) != Double.doubleToLongBits(b[bFrom + i])) {
        return i;
      }
    }
    return aLength != bLength ? length : -1;
  }

  public static int mismatchObject(Object[] a, Object[] b) {
    int length = Math.min(a.length, b.length);
    if (a == b) {
      return -1;
    }
    for (int i = 0; i < length; i++) {
      if (!Objects.equals(a[i], b[i])) {
        return i;
      }
    }
    return a.length != b.length ? length : -1;
  }

  public static int mismatchObjectRange(
      Object[] a, int aFrom, int aTo, Object[] b, int bFrom, int bTo) {
    checkValidRange(a.length, aFrom, aTo);
    checkValidRange(b.length, bFrom, bTo);
    int aLength = aTo - aFrom;
    int bLength = bTo - bFrom;
    int length = Math.min(aLength, bLength);
    for (int i = 0; i < length; i++) {
      if (!Objects.equals(a[aFrom + i], b[bFrom + i])) {
        return i;
      }
    }
    return aLength != bLength ? length : -1;
  }

  public static <T> int mismatchComparator(T[] a, T[] b, Comparator<? super T> cmp) {
    Objects.requireNonNull(cmp);
    int length = Math.min(a.length, b.length);
    if (a == b) {
      return -1;
    }
    for (int i = 0; i < length; i++) {
      if (cmp.compare(a[i], b[i]) != 0) {
        return i;
      }
    }
    return a.length != b.length ? length : -1;
  }

  public static <T> int mismatchComparatorRange(
      T[] a, int aFrom, int aTo, T[] b, int bFrom, int bTo, Comparator<? super T> cmp) {
    Objects.requireNonNull(cmp);
    checkValidRange(a.length, aFrom, aTo);
    checkValidRange(b.length, bFrom, bTo);
    int aLength = aTo - aFrom;
    int bLength = bTo - bFrom;
    int length = Math.min(aLength, bLength);
    for (int i = 0; i < length; i++) {
      if (cmp.compare(a[aFrom + i], b[bFrom + i]) != 0) {
        return i;
      }
    }
    return aLength != bLength ? length : -1;
  }
}
