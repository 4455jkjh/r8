// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.util.function.Supplier;

public class AssertionUtils {

  public static boolean assertNotNull(Object o) {
    assert o != null;
    return true;
  }

  public static int checkNotNegative(int i) {
    if (i < 0) {
      throw new AssertionError("Expected integer value to be non-negative");
    }
    return i;
  }

  public static boolean forTesting(InternalOptions options, Supplier<Boolean> test) {
    return options.testing.enableTestAssertions ? test.get() : true;
  }
}
