// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.timing;

import static androidx.tracing.Tasks_desktopKt.currentTaskId;
import static com.android.tools.r8.utils.ExceptionUtils.unwrapExecutionException;

import androidx.tracing.CounterTrack;
import androidx.tracing.ProcessTrack;
import androidx.tracing.TraceContext;
import androidx.tracing.wire.TraceDriver;
import androidx.tracing.wire.TraceSink;
import androidx.tracing.wire.TraceSinks;
import com.android.tools.r8.utils.InternalOptions;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.IntFunction;
import kotlinx.coroutines.Dispatchers;
import okio.BufferedSink;
import okio.Okio;

public class PerfettoTiming extends TimingImplBase {
  // The API now allows you to specify the category and in the future let you conditionally turn on
  // categories.
  static final String TRACE_CATEGORY = "R8";

  private final TraceDriver traceDriver;
  private final ProcessTrack processTrack;
  private final long mainThreadId;
  final long minDurationNanos;
  final String batchTitle;
  private final ConcurrentHashMap<Long, PerfettoThreadStack> threadStacks =
      new ConcurrentHashMap<>();
  private CounterTrack memoryTrack;
  private Future<Void> memoryTracker;
  private volatile boolean memoryTrackerActive = true;
  private CounterTrack uncommittedDexItemsTrack;

  private final Set<Long> tracedWorkerThreadIds = ConcurrentHashMap.newKeySet();

  public PerfettoTiming(String title, InternalOptions options, ExecutorService executorService) {
    int minDurationMs = options.getPerfettoTraceMinValueMs();
    this.minDurationNanos = TimeUnit.MILLISECONDS.toNanos(minDurationMs);
    this.batchTitle = "slices < " + minDurationMs + "ms";
    int sequenceId = 1;
    TraceSink sink;
    if (options.perfettoTraceDumpFile != null) {
      BufferedSink bufferedSink;
      try {
        File file = new File(options.perfettoTraceDumpFile);
        if (file.getParentFile() != null) {
          file.getParentFile().mkdirs();
        }
        bufferedSink = Okio.buffer(Okio.sink(file));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      sink = new TraceSink(sequenceId, bufferedSink, Dispatchers.getIO());
    } else {
      File directory = new File(options.perfettoTraceDumpDirectory);
      sink = TraceSinks.TraceSink(directory, sequenceId);
    }
    // Populates the process track correctly.
    traceDriver = new TraceDriver(sink);
    TraceContext traceContext = traceDriver.getContext();
    processTrack = traceContext.getProcess();
    mainThreadId = Thread.currentThread().getId();
    // ProcessTrack.<init> pre-creates a ThreadTrack for the current thread using
    // Thread.currentThread().getName(). Remove it so the track is created with the name
    // "Main thread" instead.
    processTrack.getThreads().remove(mainThreadId);
    processTrack.l1ThreadTrack = null;
    stack =
        new PerfettoThreadStack(
            this,
            mainThreadId,
            true,
            () ->
                processTrack.getOrCreateThreadTrack(mainThreadId, currentTaskId(), "Main thread"));
    begin(title);
    // Memory tracking requires an executor service.
    if (executorService != null) {
      memoryTrack = processTrack.getOrCreateCounterTrack("Memory");
      uncommittedDexItemsTrack = processTrack.getOrCreateCounterTrack("Uncommitted dex items");
      memoryTracker =
          executorService.submit(
              () -> {
                while (true) {
                  // Check the memoryCounterActive flag every 250ms.
                  for (int i = 0; i < 4; i++) {
                    Thread.sleep(250);
                    if (!memoryTrackerActive) {
                      return null;
                    }
                  }
                  // Update the memory counter every 1s.
                  memoryTrack.setValue(
                      Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
                  uncommittedDexItemsTrack.setValue(
                      options.dexItemFactory().getNumberOfUncommittedItems());
                }
              });
    }
  }

  long readClock() {
    return System.nanoTime();
  }

  @Override
  public Timing createThreadTiming(String title, InternalOptions options) {
    return createThreadTiming(title, Function.identity(), options);
  }

  @Override
  public <T> Timing createThreadTiming(
      T context, Function<T, String> titleSupplier, InternalOptions options) {
    long threadId = Thread.currentThread().getId();
    if (threadId == mainThreadId) {
      return begin(context, titleSupplier);
    }
    if (options.perfettoTraceThreads >= 0) {
      // Check if tracing should be enabled for the current thread.
      if (tracedWorkerThreadIds.size() < options.perfettoTraceThreads) {
        synchronized (tracedWorkerThreadIds) {
          if (tracedWorkerThreadIds.size() < options.perfettoTraceThreads) {
            tracedWorkerThreadIds.add(threadId);
          }
        }
      }
      if (!tracedWorkerThreadIds.contains(threadId)) {
        return Timing.empty();
      }
    }
    PerfettoThreadStack threadStack =
        threadStacks.computeIfAbsent(
            threadId,
            id -> {
              // Capture the task id here since the track may be created when closing the trace.
              long taskId = currentTaskId();
              return new PerfettoThreadStack(
                  this, id, false, () -> processTrack.getOrCreateThreadTrack(id, taskId, "Worker"));
            });
    assert threadStack.depth() == 0 : threadStack.depth();
    return new PerfettoThreadTiming(threadStack).begin(context, titleSupplier);
  }

  @Override
  public Timing createThreadTiming(
      int value, IntFunction<String> titleSupplier, InternalOptions options) {
    return createThreadTiming(titleSupplier.apply(value), options);
  }

  @Override
  public void report() {
    end();
    assert stack.depth() == 0 : stack.depth();
    awaitMemoryTracker();
    // All thread timings have finished at this point, so emit their pending root-level batches.
    threadStacks.values().forEach(PerfettoThreadStack::flushPendingRootBatch);
    traceDriver.close();
  }

  @Override
  public Timing endAll() {
    while (stack.depth() > 1) {
      end();
    }
    return this;
  }

  private void awaitMemoryTracker() {
    if (memoryTracker != null) {
      // Signal to the memory tracker to stop.
      memoryTrackerActive = false;
      // Await the memory tracker.
      try {
        memoryTracker.get();
      } catch (InterruptedException e) {
        throw new RuntimeException("Interrupted while waiting for future.", e);
      } catch (ExecutionException e) {
        throw unwrapExecutionException(e);
      }
      memoryTrack = null;
      memoryTracker = null;
      uncommittedDexItemsTrack = null;
    }
  }
}
