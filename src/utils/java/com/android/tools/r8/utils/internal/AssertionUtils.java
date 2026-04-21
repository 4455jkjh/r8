// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.internal;

import com.android.tools.r8.utils.internal.exceptions.Unreachable;

public class AssertionUtils {

  public static boolean assertionsEnabled() {
    boolean assertionsEnabled = false;
    //noinspection AssertWithSideEffects
    assert assertionsEnabled = true; // Intentional side-effect.
    return assertionsEnabled;
  }

  public static void checkAssertionsEnabled() {
    if (!assertionsEnabled()) {
      throw new Unreachable();
    }
  }

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
}
