package com.android.tools.r8.ir.desugar.backports;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

public class ExecutorServiceMethods {
  // Stub out android.os.Build$VERSION as it does not exist when building R8.
  private static class AndroidOsBuildVersionStub {
    public static int SDK_INT;
  }

  public static void closeExecutorService(ExecutorService executorService) {
    if (AndroidOsBuildVersionStub.SDK_INT > 23) {
      if (executorService == ForkJoinPool.commonPool()) {
        return;
      }
    }
    boolean terminated = executorService.isTerminated();
    if (!terminated) {
      executorService.shutdown();
      boolean interrupted = false;
      while (!terminated) {
        try {
          terminated = executorService.awaitTermination(1L, TimeUnit.DAYS);
        } catch (InterruptedException e) {
          if (!interrupted) {
            executorService.shutdownNow();
            interrupted = true;
          }
        }
      }
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
