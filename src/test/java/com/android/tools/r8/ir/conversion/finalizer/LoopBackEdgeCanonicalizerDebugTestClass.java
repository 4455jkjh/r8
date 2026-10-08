// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion.finalizer;

// The line numbers are used by LoopBackEdgeCanonicalizerDebugTest.
public class LoopBackEdgeCanonicalizerDebugTestClass {

  static int sumPositives(int[] a) {
    int sum = 0;
    int i = 0;
    while (true) {
      if (i >= a.length) {
        break;
      }
      int v = a[i++];
      if (v < 0) {
        continue;
      }
      sum += v;
    }
    return sum;
  }

  public static void main(String[] args) {
    System.out.println(sumPositives(new int[] {-1, 2}));
  }
}
