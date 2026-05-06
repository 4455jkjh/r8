// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.redex;

import com.android.tools.r8.NeverInline;
import java.util.Arrays;

public class TestClass {

  public static void main(String[] args) {
    foo(); // This line must be line 12 for the test to pass.
  }

  @NeverInline
  public static void foo() {
    String[] strings = new String[] {"1", "1", "1", "1", "1", "1", "1", "1", "1", "1", "1", "1"};
    if (System.out != null) {
      throw new RuntimeException("Crash!"); // This line must be line 19 for the test to pass.
    }
    throw new RuntimeException("" + Arrays.hashCode(strings));
  }
}
