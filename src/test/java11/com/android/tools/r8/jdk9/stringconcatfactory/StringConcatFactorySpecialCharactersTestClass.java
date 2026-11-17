// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk9.stringconcatfactory;

public class StringConcatFactorySpecialCharactersTestClass {

  public static void testSpecialCharacters(String s1, String s2) {
    System.out.println("Special: \1" + s1 + "\2" + s2 + "\1\2");
  }

  public static void main(String[] args) {
    testSpecialCharacters(args.length == 0 ? "a" : "b", args.length == 0 ? "c" : "d");
  }
}
