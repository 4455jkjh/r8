// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi.mockdata;

// Class to use as data for the compilation.
public class MockClassWithPrivateMethod {

  public static void main(String[] args) {
    greet();
  }

  private static void greet() {
    System.out.println("Hello world!");
  }
}
