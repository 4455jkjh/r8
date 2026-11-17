// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk9.stringconcatfactory;

public class StringConcatFactoryTestClass {

  public static void testPrimitives(
      boolean z, char c, byte b, short s, int i, long l, float f, double d) {
    System.out.println(
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
            + d);
  }

  public static void testStrings(String s1, String s2) {
    System.out.println("Strings: " + s1 + ", " + s2);
  }

  public static void testNoConstants(int i, long l, String s) {
    System.out.println("" + i + l + s);
  }

  public static void main(String[] args) {
    testPrimitives(
        args.length == 0,
        (char) (65 + args.length),
        (byte) args.length,
        (short) (args.length + 1),
        args.length + 2,
        args.length + 3,
        args.length + 4,
        args.length + 5);
    testStrings(args.length == 0 ? "a" : "b", args.length == 0 ? "c" : "d");
    testNoConstants(args.length + 2, args.length + 3, args.length == 0 ? "a" : "b");
  }
}
