// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks.gc;

public class GcUtils {

  public static boolean isConcurrentGcCollector(String name) {
    return name.equals("G1 Concurrent GC");
  }

  public static boolean isOldGcCollector(String name) {
    return name.equals("G1 Old Generation");
  }

  public static boolean isYoungGcCollector(String name) {
    return name.equals("G1 Young Generation");
  }
}
