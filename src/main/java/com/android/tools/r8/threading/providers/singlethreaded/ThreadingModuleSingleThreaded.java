// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.threading.providers.singlethreaded;

import com.android.tools.r8.threading.ThreadingModule;
import com.google.common.util.concurrent.Futures;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class ThreadingModuleSingleThreaded implements ThreadingModule {

  @Override
  public <T> Future<T> submit(Callable<T> task, ExecutorService executorService)
      throws ExecutionException {
    try {
      T value = task.call();
      return Futures.immediateFuture(value);
    } catch (Exception e) {
      throw new ExecutionException(e);
    }
  }

  @Override
  public <T> void awaitFutures(List<Future<T>> futures) throws ExecutionException {
    assert allDone(futures);
  }

  private <T> boolean allDone(List<Future<T>> futures) {
    for (Future<?> future : futures) {
      if (!future.isDone()) {
        return false;
      }
    }
    return true;
  }
}
