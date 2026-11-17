// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.timing;

import static androidx.tracing.TraceEventKt.TRACE_EVENT_TYPE_BEGIN;
import static androidx.tracing.TraceEventKt.TRACE_EVENT_TYPE_END;

import androidx.tracing.ThreadTrack;
import androidx.tracing.TraceEvent;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Per-thread stack of open slices that defers emission until a slice is known to meet the minimum
 * duration.
 *
 * <p>Slices below the minimum duration are dropped and merged into batch slices.
 *
 * <p>The pending batch of dropped slices at frame depth {@code frame} is stored at index {@code
 * frame} of {@code batchStarts} and {@code batchEnds} (sized {@code capacity + 1} so that {@code
 * flushBatch(frame + 1)} can check for dropped children of the deepest frame).
 */
class PerfettoThreadStack {
  private static final int INITIAL_CAPACITY = 16;

  // Maximum gap between consecutive dropped slices for them to be merged into the same batch.
  private static final long MAX_BATCH_GAP_NANOS = TimeUnit.MILLISECONDS.toNanos(5);

  private final PerfettoTiming timing;
  private final long threadId;
  private final boolean alwaysEmitRoot;
  private final Supplier<ThreadTrack> trackSupplier;

  // Open slice stack indexed by frame depth.
  // Context object passed to titleSuppliers[frame] to compute the frame's title on emission.
  private Object[] contexts = new Object[INITIAL_CAPACITY];

  // Function to compute the frame's title on emission (Function.identity() for String titles).
  @SuppressWarnings("unchecked")
  private Function<Object, String>[] titleSuppliers = new Function[INITIAL_CAPACITY];

  // Start timestamp in nanoseconds of each open frame.
  private long[] startTimes = new long[INITIAL_CAPACITY];
  private int depth = 0;
  private int emittedDepth = 0;
  private ThreadTrack threadTrack;

  // Pending batches of dropped slices indexed by frame depth.
  // Start timestamp of the first dropped slice in the batch (0 if there is no pending batch).
  private long[] batchStarts = new long[INITIAL_CAPACITY + 1];
  // End timestamp of the most recent dropped slice in the batch.
  private long[] batchEnds = new long[INITIAL_CAPACITY + 1];

  PerfettoThreadStack(
      PerfettoTiming timing,
      long threadId,
      boolean alwaysEmitRoot,
      Supplier<ThreadTrack> trackSupplier) {
    this.timing = timing;
    this.threadId = threadId;
    this.alwaysEmitRoot = alwaysEmitRoot;
    this.trackSupplier = trackSupplier;
  }

  int depth() {
    return depth;
  }

  void begin(String title) {
    begin(title, Function.identity());
  }

  @SuppressWarnings("unchecked")
  <T> void begin(T context, Function<T, String> titleSupplier) {
    assert threadId == Thread.currentThread().getId();
    if (depth == contexts.length) {
      int newCapacity = contexts.length * 2;
      contexts = Arrays.copyOf(contexts, newCapacity);
      titleSuppliers = Arrays.copyOf(titleSuppliers, newCapacity);
      startTimes = Arrays.copyOf(startTimes, newCapacity);
      batchStarts = Arrays.copyOf(batchStarts, newCapacity + 1);
      batchEnds = Arrays.copyOf(batchEnds, newCapacity + 1);
    }
    contexts[depth] = context;
    titleSuppliers[depth] = (Function<Object, String>) titleSupplier;
    startTimes[depth] = timing.readClock();
    depth++;
  }

  void end() {
    assert threadId == Thread.currentThread().getId();
    assert depth > 0;
    int frame = --depth;
    long endNanos = timing.readClock();
    // If frame < emittedDepth, a descendant slice was already emitted, which also emitted the
    // BEGIN event for this frame, so we must emit its matching END event.
    if (frame < emittedDepth
        || endNanos - startTimes[frame] >= timing.minDurationNanos
        || (alwaysEmitRoot && frame == 0)) {
      emitFrame(frame, endNanos);
    } else {
      // The frame is dropped, including its pending child batch, and added to the batch at this
      // depth.
      clearBatch(frame + 1);
      if (frame == 0 && hasBatch(0) && startTimes[0] - batchEnds[0] > MAX_BATCH_GAP_NANOS) {
        flushBatch(0);
      }
      addToBatch(frame, startTimes[frame], endNanos);
    }
    contexts[frame] = null;
    titleSuppliers[frame] = null;
  }

  /** Emits the pending batch of root-level slices. Called when the trace is closed. */
  void flushPendingRootBatch() {
    flushBatch(0);
  }

  private void emitFrame(int frame, long endNanos) {
    assert frame >= emittedDepth - 1;
    for (int i = emittedDepth; i <= frame; i++) {
      flushBatch(i);
      emitBegin(titleSuppliers[i].apply(contexts[i]), startTimes[i]);
    }
    flushBatch(frame + 1);
    emitEnd(endNanos);
    emittedDepth = frame;
  }

  private boolean hasBatch(int frame) {
    return batchStarts[frame] != 0;
  }

  private void addToBatch(int frame, long startNanos, long endNanos) {
    if (!hasBatch(frame)) {
      batchStarts[frame] = startNanos;
    }
    batchEnds[frame] = endNanos;
  }

  private void flushBatch(int frame) {
    if (!hasBatch(frame)) {
      return;
    }
    long startNanos = batchStarts[frame];
    long endNanos = batchEnds[frame];
    batchStarts[frame] = 0;
    if (frame > 0 || endNanos - startNanos >= timing.minDurationNanos) {
      emitBegin(timing.batchTitle, startNanos);
      emitEnd(endNanos);
    }
  }

  private void clearBatch(int frame) {
    batchStarts[frame] = 0;
  }

  private void ensureTrackAndPreamble(long timestampNanos) {
    if (threadTrack == null) {
      threadTrack = trackSupplier.get();
    }
    if (!threadTrack.preamble) {
      TraceEvent preambleEvent = threadTrack.preamblePacket$tracing();
      preambleEvent.timestamp = timestampNanos;
      threadTrack.dispatchTraceEvent$tracing(preambleEvent, true);
      threadTrack.preamble = true;
    }
  }

  private void emitBegin(String title, long startTimeNanos) {
    ensureTrackAndPreamble(startTimeNanos);
    TraceEvent event = threadTrack.obtainTraceEvent$tracing();
    event.primaryCategory = PerfettoTiming.TRACE_CATEGORY;
    event.type = TRACE_EVENT_TYPE_BEGIN;
    event.trackUuid = threadTrack.uuid;
    event.timestamp = startTimeNanos;
    event.name = title;
    threadTrack.dispatchTraceEvent$tracing(event, false);
  }

  private void emitEnd(long endTimeNanos) {
    TraceEvent event = threadTrack.obtainTraceEvent$tracing();
    event.type = TRACE_EVENT_TYPE_END;
    event.trackUuid = threadTrack.uuid;
    event.timestamp = endTimeNanos;
    threadTrack.dispatchTraceEvent$tracing(event, false);
  }
}
