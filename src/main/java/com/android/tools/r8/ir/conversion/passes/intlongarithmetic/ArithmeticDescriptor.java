// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes.intlongarithmetic;

public interface ArithmeticDescriptor {

  int evaluate(int left, int right);

  long evaluate(long left, long right);
}
