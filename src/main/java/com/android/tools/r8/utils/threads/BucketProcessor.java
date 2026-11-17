// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.threads;

import com.android.tools.r8.threading.TaskCollection;
import com.android.tools.r8.utils.InternalOptions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.RandomAccess;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public abstract class BucketProcessor<T, R> {

  // Balances parallel task granularity on medium inputs against per-bucket overhead.
  private static final int DEFAULT_BUCKET_SIZE = 512;

  protected int getBucketSize() {
    return DEFAULT_BUCKET_SIZE;
  }

  protected abstract R emptyResult();

  protected abstract R processBucket(List<T> bucket) throws Exception;

  /** Combines adjacent bucket results in submission order; must be associative. */
  protected abstract R merge(R left, R right);

  public final R process(
      Collection<T> items, InternalOptions options, ExecutorService executorService)
      throws ExecutionException {
    if (items.isEmpty()) {
      return emptyResult();
    }
    List<T> itemList =
        items instanceof List && items instanceof RandomAccess
            ? (List<T>) items
            : new ArrayList<>(items);
    int total = itemList.size();
    int bucketSize = getBucketSize();
    assert bucketSize > 0;
    if (total <= bucketSize || options.getThreadingModule().isSingleThreaded()) {
      try {
        return processBucket(itemList);
      } catch (Exception e) {
        throw new ExecutionException(e);
      }
    }
    int numBuckets = (total + bucketSize - 1) / bucketSize;
    TaskCollection<R> tasks = new TaskCollection<>(options, executorService, numBuckets);
    for (int start = 0; start < total; start += bucketSize) {
      List<T> bucket = itemList.subList(start, Math.min(total, start + bucketSize));
      tasks.submit(() -> processBucket(bucket));
    }
    return mergeAll(tasks.awaitWithResults());
  }

  private R mergeAll(List<R> current) {
    while (current.size() > 1) {
      int size = current.size();
      List<R> next = new ArrayList<>((size + 1) / 2);
      for (int i = 0; i < size; i += 2) {
        next.add(i + 1 < size ? merge(current.get(i), current.get(i + 1)) : current.get(i));
      }
      current = next;
    }
    return current.get(0);
  }
}
