// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk9.stringconcatfactory;

public class StringConcatFactoryArrayTestClass {

  public static void testArrays(
      boolean z[],
      byte b[],
      char c[],
      short s[],
      int i[],
      long l[],
      float f[],
      double d[],
      String[] str,
      Object[] obj) {
    System.out.print(
        "Values: "
            + z
            + ", "
            + c
            + ", "
            + b
            + ", "
            + s
            + ", "
            + i
            + ", "
            + l
            + ", "
            + f
            + ", "
            + d
            + ", "
            + str
            + ", "
            + obj);
  }

  public static void main(String[] args) {
    testArrays(
        new boolean[] {args.length == 0, args.length != 0},
        new byte[] {(byte) args.length, (byte) (args.length + 1)},
        new char[] {(char) (65 + args.length), (char) (66 + args.length)},
        new short[] {(short) (args.length + 2), (short) (args.length + 3)},
        new int[] {args.length + 4, args.length + 5},
        new long[] {args.length + 6, args.length + 7},
        new float[] {args.length + 8, args.length + 9},
        new double[] {args.length + 10, args.length + 11},
        new String[] {String.valueOf(args.length + 12), String.valueOf(args.length + 13)},
        new Object[] {Integer.valueOf(args.length + 14), Integer.valueOf(args.length + 15)});
  }
}
