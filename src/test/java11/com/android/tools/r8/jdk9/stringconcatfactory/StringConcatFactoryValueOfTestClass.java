// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk9.stringconcatfactory;

public class StringConcatFactoryValueOfTestClass {

  public static void testPrimitivesValueOf(
      boolean z, char c, byte b, short s, int i, long l, float f, double d) {
    System.out.println(
        "Values: "
            + String.valueOf(z)
            + ", "
            + String.valueOf(c)
            + ", "
            + String.valueOf(b)
            + ", "
            + String.valueOf(s)
            + ", "
            + String.valueOf(i)
            + ", "
            + String.valueOf(l)
            + ", "
            + String.valueOf(f)
            + ", "
            + String.valueOf(d));
  }

  public static void main(String[] args) {
    testPrimitivesValueOf(
        args.length == 0,
        (char) (65 + args.length),
        (byte) args.length,
        (short) (args.length + 1),
        args.length + 2,
        args.length + 3,
        args.length + 4,
        args.length + 5);
  }
}
