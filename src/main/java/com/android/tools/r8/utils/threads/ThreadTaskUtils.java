// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.threads;

import com.android.tools.r8.threading.TaskCollection;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.timing.Timing;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class ThreadTaskUtils {

  public static void processTasks(
      ExecutorService executorService, InternalOptions options, Timing timing, ThreadTask... tasks)
      throws ExecutionException {
    assert tasks.length > 0;
    int tasksToRun = 0;
    for (ThreadTask task : tasks) {
      if (task.shouldRun()) {
        tasksToRun++;
      }
    }
    TaskCollection<?> taskCollection = new TaskCollection<>(options, executorService, tasksToRun);
    for (ThreadTask task : tasks) {
      if (task.shouldRun()) {
        processTask(task, taskCollection);
      }
    }
    taskCollection.await();
    for (ThreadTask task : tasks) {
      if (task.shouldRun()) {
        task.onJoin();
      }
    }
  }

  private static void processTask(ThreadTask task, TaskCollection<?> taskCollection)
      throws ExecutionException {
    if (task.shouldRunOnThread()) {
      taskCollection.submit(() -> task.runWithRuntimeException(Timing.empty()));
    } else {
      task.runWithRuntimeException(Timing.empty());
    }
  }
}
