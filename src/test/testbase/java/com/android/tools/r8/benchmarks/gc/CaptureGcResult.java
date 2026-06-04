// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks.gc;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

public class CaptureGcResult {

  private final long oldCount;
  private final long oldTimeNanos;
  private final long youngCount;
  private final long youngTimeNanos;

  public CaptureGcResult(long oldCount, long oldTimeNanos, long youngCount, long youngTimeNanos) {
    this.oldCount = oldCount;
    this.oldTimeNanos = oldTimeNanos;
    this.youngCount = youngCount;
    this.youngTimeNanos = youngTimeNanos;
  }

  /** Captures a snapshot of the current GC state. */
  public static CaptureGcResult capture() {
    List<GarbageCollectorMXBean> beans = ManagementFactory.getGarbageCollectorMXBeans();
    long youngCount = 0;
    long youngTimeMs = 0;
    long oldCount = 0;
    long oldTimeMs = 0;
    for (GarbageCollectorMXBean bean : beans) {
      long count = bean.getCollectionCount();
      long time = bean.getCollectionTime();
      if (count == -1) {
        continue; // Collection count/time is not supported by this JVM
      }
      String collectorName = bean.getName();
      if (GcUtils.isConcurrentGcCollector(collectorName)) {
        // Concurrent gc currently not tracked.
      } else if (GcUtils.isOldGcCollector(collectorName)) {
        oldCount += count;
        oldTimeMs += time;
      } else if (GcUtils.isYoungGcCollector(collectorName)) {
        youngCount += count;
        youngTimeMs += time;
      } else {
        throw new RuntimeException("Unrecognized collector: " + bean.getName());
      }
    }
    return new CaptureGcResult(
        oldCount, oldTimeMs * 1_000_000, youngCount, youngTimeMs * 1_000_000);
  }

  /** Computes the difference (delta) between this snapshot and a later snapshot. */
  public CaptureGcResult computeDelta(CaptureGcResult after) {
    return new CaptureGcResult(
        after.oldCount - oldCount,
        after.oldTimeNanos - oldTimeNanos,
        after.youngCount - youngCount,
        after.youngTimeNanos - youngTimeNanos);
  }

  public long getOldCount() {
    return oldCount;
  }

  public long getOldTimeNanos() {
    return oldTimeNanos;
  }

  public long getYoungCount() {
    return youngCount;
  }

  public long getYoungTimeNanos() {
    return youngTimeNanos;
  }
}
