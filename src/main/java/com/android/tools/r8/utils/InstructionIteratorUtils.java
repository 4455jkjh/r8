// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.ir.code.InstructionIterator;

public class InstructionIteratorUtils {

  public static void skip(InstructionIterator iterator, int times) {
    if (times >= 0) {
      for (int i = 0; i < times; i++) {
        iterator.next();
      }
    } else {
      for (int i = 0; i > times; i--) {
        iterator.previous();
      }
    }
  }
}
