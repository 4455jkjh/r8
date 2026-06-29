// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tools.apkanalyzer;

class MinMaxTotalStats {

  long min = Long.MAX_VALUE;
  long max = Long.MIN_VALUE;
  long total = 0;
  int count = 0;

  void add(long number) {
    count++;
    min = Math.min(min, number);
    max = Math.max(max, number);
    total += number;
  }

  MinMaxTotalStats finish() {
    if (count == 0) {
      min = 0;
      max = 0;
    }
    return this;
  }
}
