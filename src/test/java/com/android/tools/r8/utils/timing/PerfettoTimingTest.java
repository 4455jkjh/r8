// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.timing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.tracing.wire.protos.MutableThreadDescriptor;
import androidx.tracing.wire.protos.MutableTrace;
import androidx.tracing.wire.protos.MutableTracePacket;
import androidx.tracing.wire.protos.MutableTrackDescriptor;
import androidx.tracing.wire.protos.MutableTrackEvent;
import androidx.tracing.wire.protos.MutableTrackEvent.Type;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.Version;
import com.android.tools.r8.utils.InternalOptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PerfettoTimingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withMinimumApiLevel().build();
  }

  private static long ms(long millis) {
    return TimeUnit.MILLISECONDS.toNanos(millis);
  }

  private static PerfettoTiming createPerfettoTiming(
      String title, InternalOptions options, AtomicLong clock) {
    return new PerfettoTiming(title, options, null) {
      @Override
      long readClock() {
        return clock.get();
      }
    };
  }

  private static MutableTrace decodeTrace(Path traceFile) throws IOException {
    assertTrue(Files.exists(traceFile));
    byte[] bytes = Files.readAllBytes(traceFile);
    assertTrue(bytes.length > 0);
    return MutableTrace.ADAPTER.decode(bytes);
  }

  private static List<MutableTracePacket> findThreadDescriptorPackets(
      MutableTrace trace, String threadName) {
    List<MutableTracePacket> matches = new ArrayList<>();
    for (MutableTracePacket packet : trace.getPacket()) {
      MutableTrackDescriptor descriptor = packet.getTrack_descriptor();
      if (descriptor != null) {
        MutableThreadDescriptor thread = descriptor.getThread();
        if (thread != null && threadName.equals(thread.getThread_name())) {
          matches.add(packet);
        }
      }
    }
    return matches;
  }

  private static boolean hasCounterTrack(MutableTrace trace, String counterName) {
    for (MutableTracePacket packet : trace.getPacket()) {
      MutableTrackDescriptor descriptor = packet.getTrack_descriptor();
      if (descriptor != null
          && descriptor.getCounter() != null
          && counterName.equals(descriptor.getName())) {
        return true;
      }
    }
    return false;
  }

  private static List<String> formatTrackEvents(MutableTrace trace, long trackUuid) {
    List<String> formatted = new ArrayList<>();
    int ignoredDepth = 0;
    for (MutableTracePacket packet : trace.getPacket()) {
      MutableTrackEvent event = packet.getTrack_event();
      if (event != null && event.getTrack_uuid() == trackUuid) {
        long timestampMs = TimeUnit.NANOSECONDS.toMillis(packet.getTimestamp());
        if (event.getType() == Type.TYPE_SLICE_BEGIN) {
          if (ignoredDepth > 0 || event.getCategories().contains("androidx.tracing")) {
            ignoredDepth++;
          } else {
            formatted.add(
                "BEGIN:" + event.getName() + "@" + timestampMs + ":" + event.getCategories());
          }
        } else if (event.getType() == Type.TYPE_SLICE_END) {
          if (ignoredDepth > 0) {
            ignoredDepth--;
          } else {
            formatted.add("END@" + timestampMs);
          }
        }
      }
    }
    return formatted;
  }

  private static void assertBalancedAndMonotonicPerTrack(MutableTrace trace) {
    Map<Long, Integer> openSlicesPerTrack = new HashMap<>();
    Map<Long, Long> lastTimestampPerTrack = new HashMap<>();
    for (MutableTracePacket packet : trace.getPacket()) {
      MutableTrackEvent event = packet.getTrack_event();
      if (event == null
          || (event.getType() != Type.TYPE_SLICE_BEGIN && event.getType() != Type.TYPE_SLICE_END)) {
        continue;
      }
      long trackUuid = event.getTrack_uuid();
      long timestamp = packet.getTimestamp();
      Long lastTimestamp = lastTimestampPerTrack.put(trackUuid, timestamp);
      assertTrue(lastTimestamp == null || lastTimestamp <= timestamp);
      int openSlices =
          openSlicesPerTrack.getOrDefault(trackUuid, 0)
              + (event.getType() == Type.TYPE_SLICE_BEGIN ? 1 : -1);
      assertTrue(openSlices >= 0);
      openSlicesPerTrack.put(trackUuid, openSlices);
    }
    openSlicesPerTrack.values().forEach(openSlices -> assertEquals(0, (int) openSlices));
  }

  @Test
  public void testLiteModeAncestorChainAndFastSiblings() throws IOException {
    Path traceFile = temp.newFolder().toPath().resolve("lite.perfetto-trace");
    AtomicLong clock = new AtomicLong(ms(1000));
    InternalOptions options = new InternalOptions();
    options.perfettoTraceDumpFile = traceFile.toString();
    options.perfettoTraceLite = true;
    options.perfettoTraceMinValueMs = 50;

    PerfettoTiming timing = createPerfettoTiming("R8", options, clock);

    clock.set(ms(1010));
    timing.begin("FastSiblingBefore");
    clock.set(ms(1049));
    timing.end();

    clock.set(ms(1060));
    timing.begin("Outer");

    clock.set(ms(1065));
    timing.begin("FastChild");
    clock.set(ms(1090));
    timing.end();

    clock.set(ms(1095));
    timing.begin("Middle");

    clock.set(ms(1100));
    timing.begin("SlowLeaf");
    clock.set(ms(1150));
    timing.end();

    clock.set(ms(1155));
    timing.begin("FastSiblingAfter");
    clock.set(ms(1190));
    timing.end();

    clock.set(ms(1195));
    timing.end();

    clock.set(ms(1200));
    timing.end();

    clock.set(ms(1210));
    timing.report();

    MutableTrace trace = decodeTrace(traceFile);

    List<MutableTracePacket> mainDescriptors = findThreadDescriptorPackets(trace, "Main thread");
    assertEquals(1, mainDescriptors.size());
    assertEquals(ms(1000), mainDescriptors.get(0).getTimestamp());
    long mainUuid = mainDescriptors.get(0).getTrack_descriptor().getUuid();

    assertEquals(
        Arrays.asList(
            "BEGIN:R8@1000:[R8]",
            "BEGIN:slices < 50ms@1010:[R8]",
            "END@1049",
            "BEGIN:Outer@1060:[R8]",
            "BEGIN:slices < 50ms@1065:[R8]",
            "END@1090",
            "BEGIN:Middle@1095:[R8]",
            "BEGIN:SlowLeaf@1100:[R8]",
            "END@1150",
            "BEGIN:slices < 50ms@1155:[R8]",
            "END@1190",
            "END@1195",
            "END@1200",
            "END@1210"),
        formatTrackEvents(trace, mainUuid));
    assertBalancedAndMonotonicPerTrack(trace);
  }

  private static void time(AtomicLong clock, Timing timing, String title, long start, long end) {
    clock.set(start);
    timing.begin(title);
    clock.set(end);
    timing.end();
  }

  @Test
  public void testLiteModeBatchesDroppedSiblings() throws IOException {
    Path traceFile = temp.newFolder().toPath().resolve("batch.perfetto-trace");
    AtomicLong clock = new AtomicLong(ms(1000));
    InternalOptions options = new InternalOptions();
    options.perfettoTraceDumpFile = traceFile.toString();
    options.perfettoTraceLite = true;
    options.perfettoTraceMinValueMs = 50;

    PerfettoTiming timing = createPerfettoTiming("R8", options, clock);
    time(clock, timing, "A", ms(1010), ms(1020));
    time(clock, timing, "A", ms(1020), ms(1030));
    time(clock, timing, "Slow1", ms(1040), ms(1090));
    time(clock, timing, "B", ms(1100), ms(1110));
    time(clock, timing, "C", ms(1110), ms(1120));
    time(clock, timing, "Slow2", ms(1130), ms(1190));
    time(clock, timing, "D", ms(1200), ms(1210));
    clock.set(ms(1250));
    timing.report();

    MutableTrace trace = decodeTrace(traceFile);
    long mainUuid =
        findThreadDescriptorPackets(trace, "Main thread").get(0).getTrack_descriptor().getUuid();
    assertEquals(
        Arrays.asList(
            "BEGIN:R8@1000:[R8]",
            "BEGIN:slices < 50ms@1010:[R8]",
            "END@1030",
            "BEGIN:Slow1@1040:[R8]",
            "END@1090",
            "BEGIN:slices < 50ms@1100:[R8]",
            "END@1120",
            "BEGIN:Slow2@1130:[R8]",
            "END@1190",
            "BEGIN:slices < 50ms@1200:[R8]",
            "END@1210",
            "END@1250"),
        formatTrackEvents(trace, mainUuid));
    assertBalancedAndMonotonicPerTrack(trace);
  }

  @Test
  public void testLiteModeFoldsDroppedParentAndEmitsDeferredAncestorsWithBatches()
      throws IOException {
    Path traceFile = temp.newFolder().toPath().resolve("fold.perfetto-trace");
    AtomicLong clock = new AtomicLong(ms(1000));
    InternalOptions options = new InternalOptions();
    options.perfettoTraceDumpFile = traceFile.toString();
    options.perfettoTraceLite = true;
    options.perfettoTraceMinValueMs = 50;

    PerfettoTiming timing = createPerfettoTiming("R8", options, clock);
    clock.set(ms(1010));
    timing.begin("Outer");
    clock.set(ms(1020));
    timing.begin("DroppedParent");
    time(clock, timing, "DroppedChild", ms(1030), ms(1040));
    time(clock, timing, "DroppedChild", ms(1040), ms(1050));
    clock.set(ms(1060));
    timing.end();
    time(clock, timing, "Dropped", ms(1070), ms(1080));
    clock.set(ms(1090));
    timing.begin("Middle");
    time(clock, timing, "Fast", ms(1100), ms(1110));
    time(clock, timing, "Slow", ms(1120), ms(1180));
    clock.set(ms(1190));
    timing.end();
    clock.set(ms(1200));
    timing.end();
    clock.set(ms(1210));
    timing.report();

    MutableTrace trace = decodeTrace(traceFile);
    long mainUuid =
        findThreadDescriptorPackets(trace, "Main thread").get(0).getTrack_descriptor().getUuid();
    assertEquals(
        Arrays.asList(
            "BEGIN:R8@1000:[R8]",
            "BEGIN:Outer@1010:[R8]",
            "BEGIN:slices < 50ms@1020:[R8]",
            "END@1080",
            "BEGIN:Middle@1090:[R8]",
            "BEGIN:slices < 50ms@1100:[R8]",
            "END@1110",
            "BEGIN:Slow@1120:[R8]",
            "END@1180",
            "END@1190",
            "END@1200",
            "END@1210"),
        formatTrackEvents(trace, mainUuid));
    assertBalancedAndMonotonicPerTrack(trace);
  }

  @Test
  public void testLiteModeBatchesRootLevelWorkerSlices() throws Exception {
    Path traceFile = temp.newFolder().toPath().resolve("worker-batch.perfetto-trace");
    AtomicLong clock = new AtomicLong(ms(1000));
    InternalOptions options = new InternalOptions();
    options.perfettoTraceDumpFile = traceFile.toString();
    options.perfettoTraceLite = true;
    options.perfettoTraceMinValueMs = 50;
    long halfMs = TimeUnit.MICROSECONDS.toNanos(500);
    long almostFiveMs = TimeUnit.MICROSECONDS.toNanos(4900);
    long overFiveMs = TimeUnit.MICROSECONDS.toNanos(5100);

    PerfettoTiming timing = createPerfettoTiming("R8", options, clock);
    Thread batchingWorker =
        new Thread(
            () -> {
              // Emitted when the idle gap before the next task exceeds 5ms.
              runTask(clock, timing, options, "M", ms(2000), ms(2020));
              runTask(clock, timing, options, "M", ms(2020), ms(2040));
              runTask(clock, timing, options, "M", ms(2040) + almostFiveMs, ms(2060));
              // Suppressed since it spans less than the minimum duration.
              runTask(clock, timing, options, "N", ms(2100), ms(2120));
              runTask(clock, timing, options, "O", ms(2120), ms(2140));
              // Emitted when the trace is closed.
              runTask(clock, timing, options, "P", ms(2140) + overFiveMs, ms(2165));
              runTask(clock, timing, options, "P", ms(2165) + almostFiveMs, ms(2190));
              runTask(clock, timing, options, "Q", ms(2190), ms(2210));
            });
    batchingWorker.start();
    batchingWorker.join();

    Thread emittingWorker =
        new Thread(
            () -> {
              runTask(clock, timing, options, "R", ms(4000), ms(4020));
              runTask(clock, timing, options, "R", ms(4020), ms(4060));
              runTask(clock, timing, options, "Slow", ms(4060) + halfMs, ms(4120));
            });
    emittingWorker.start();
    emittingWorker.join();

    clock.set(ms(5000));
    timing.report();

    MutableTrace trace = decodeTrace(traceFile);
    List<MutableTracePacket> workerDescriptors = findThreadDescriptorPackets(trace, "Worker");
    assertEquals(2, workerDescriptors.size());
    List<List<String>> workerEvents = new ArrayList<>();
    for (MutableTracePacket descriptor : workerDescriptors) {
      workerEvents.add(formatTrackEvents(trace, descriptor.getTrack_descriptor().getUuid()));
    }
    assertTrue(
        workerEvents.contains(
            Arrays.asList(
                "BEGIN:slices < 50ms@2000:[R8]",
                "END@2060",
                "BEGIN:slices < 50ms@2145:[R8]",
                "END@2210")));
    assertTrue(
        workerEvents.contains(
            Arrays.asList(
                "BEGIN:slices < 50ms@4000:[R8]", "END@4060", "BEGIN:Slow@4060:[R8]", "END@4120")));
    assertBalancedAndMonotonicPerTrack(trace);
  }

  private static void runTask(
      AtomicLong clock,
      PerfettoTiming timing,
      InternalOptions options,
      String title,
      long start,
      long end) {
    clock.set(start);
    Timing threadTiming = timing.createThreadTiming(title, options);
    clock.set(end);
    threadTiming.end();
    threadTiming.notifyThreadTimingFinished();
  }

  @Test
  public void testFullModeDoesNotBatch() throws IOException {
    Path traceFile = temp.newFolder().toPath().resolve("full.perfetto-trace");
    AtomicLong clock = new AtomicLong(ms(1000));
    InternalOptions options = new InternalOptions();
    options.perfettoTraceDumpFile = traceFile.toString();
    assertFalse(options.perfettoTraceLite);

    PerfettoTiming timing = createPerfettoTiming("R8", options, clock);
    // Slices shorter than the lite default of 2ms are still emitted one by one.
    time(clock, timing, "A", ms(1010), ms(1011));
    time(clock, timing, "A", ms(1011), ms(1012));
    clock.set(ms(1020));
    timing.report();

    MutableTrace trace = decodeTrace(traceFile);
    long mainUuid =
        findThreadDescriptorPackets(trace, "Main thread").get(0).getTrack_descriptor().getUuid();
    assertEquals(
        Arrays.asList(
            "BEGIN:R8@1000:[R8]",
            "BEGIN:A@1010:[R8]",
            "END@1011",
            "BEGIN:A@1011:[R8]",
            "END@1012",
            "END@1020"),
        formatTrackEvents(trace, mainUuid));
    assertBalancedAndMonotonicPerTrack(trace);
  }

  @Test
  public void testWorkerThreadsBelowAndAboveThreshold() throws Exception {
    Path fastOnlyFile = temp.newFolder().toPath().resolve("fast-worker.perfetto-trace");
    AtomicLong clock = new AtomicLong(ms(1000));
    InternalOptions fastOptions = new InternalOptions();
    fastOptions.perfettoTraceDumpFile = fastOnlyFile.toString();
    fastOptions.perfettoTraceLite = true;
    fastOptions.perfettoTraceMinValueMs = 50;

    PerfettoTiming fastTiming = createPerfettoTiming("R8", fastOptions, clock);
    Thread fastWorker =
        new Thread(
            () -> {
              clock.set(ms(1100));
              Timing workerTiming = fastTiming.createThreadTiming("WorkerRootFast", fastOptions);
              clock.set(ms(1110));
              workerTiming.begin("WorkerChildFast");
              clock.set(ms(1140));
              workerTiming.end();
              clock.set(ms(1149));
              workerTiming.end();
              workerTiming.notifyThreadTimingFinished();
            });
    fastWorker.start();
    fastWorker.join();
    clock.set(ms(1450));
    fastTiming.report();

    MutableTrace fastTrace = decodeTrace(fastOnlyFile);
    assertEquals(0, findThreadDescriptorPackets(fastTrace, "Worker").size());

    Path mixedFile = temp.newFolder().toPath().resolve("mixed-worker.perfetto-trace");
    clock.set(ms(2000));
    InternalOptions mixedOptions = new InternalOptions();
    mixedOptions.perfettoTraceDumpFile = mixedFile.toString();
    mixedOptions.perfettoTraceLite = true;
    mixedOptions.perfettoTraceMinValueMs = 50;

    PerfettoTiming mixedTiming = createPerfettoTiming("R8", mixedOptions, clock);
    Thread fastWorker2 =
        new Thread(
            () -> {
              clock.set(ms(2050));
              Timing workerTiming = mixedTiming.createThreadTiming("DroppedWorker", mixedOptions);
              clock.set(ms(2090));
              workerTiming.end();
              workerTiming.notifyThreadTimingFinished();
            });
    fastWorker2.start();
    fastWorker2.join();

    Thread slowWorker =
        new Thread(
            () -> {
              clock.set(ms(2300));
              Timing workerTiming = mixedTiming.createThreadTiming("WorkerRoot", mixedOptions);
              clock.set(ms(2310));
              workerTiming.begin("FastStep");
              clock.set(ms(2340));
              workerTiming.end();
              clock.set(ms(2350));
              workerTiming.begin("SlowStep");
              clock.set(ms(2400));
              workerTiming.end();
              clock.set(ms(2410));
              workerTiming.end();
              workerTiming.notifyThreadTimingFinished();
            });
    slowWorker.start();
    slowWorker.join();

    clock.set(ms(2750));
    mixedTiming.report();

    MutableTrace mixedTrace = decodeTrace(mixedFile);
    List<MutableTracePacket> workerDescriptors = findThreadDescriptorPackets(mixedTrace, "Worker");
    assertEquals(1, workerDescriptors.size());
    assertEquals(ms(2300), workerDescriptors.get(0).getTimestamp());
    long workerUuid = workerDescriptors.get(0).getTrack_descriptor().getUuid();
    assertEquals(
        Arrays.asList(
            "BEGIN:WorkerRoot@2300:[R8]",
            "BEGIN:slices < 50ms@2310:[R8]",
            "END@2340",
            "BEGIN:SlowStep@2350:[R8]",
            "END@2400",
            "END@2410"),
        formatTrackEvents(mixedTrace, workerUuid));
    assertBalancedAndMonotonicPerTrack(mixedTrace);
  }

  @Test
  public void testShortCompilationEmitsMainThreadRootSlice() throws IOException {
    Path traceFile = temp.newFolder().toPath().resolve("short.perfetto-trace");
    AtomicLong clock = new AtomicLong(ms(500));
    InternalOptions options = new InternalOptions();
    options.perfettoTraceDumpFile = traceFile.toString();
    options.perfettoTraceLite = true;
    options.perfettoTraceMinValueMs = 50;

    PerfettoTiming timing = createPerfettoTiming("R8", options, clock);
    clock.set(ms(510));
    timing.begin("QuickPhase");
    clock.set(ms(540));
    timing.end();
    clock.set(ms(580));
    timing.report();

    MutableTrace trace = decodeTrace(traceFile);
    List<MutableTracePacket> mainDescriptors = findThreadDescriptorPackets(trace, "Main thread");
    assertEquals(1, mainDescriptors.size());
    assertEquals(ms(500), mainDescriptors.get(0).getTimestamp());
    long mainUuid = mainDescriptors.get(0).getTrack_descriptor().getUuid();
    assertEquals(
        Arrays.asList("BEGIN:R8@500:[R8]", "BEGIN:slices < 50ms@510:[R8]", "END@540", "END@580"),
        formatTrackEvents(trace, mainUuid));
  }

  @Test
  public void testEndAllAndTruncateExistingTraceFileAndCreateParentDirs() throws IOException {
    Path traceFile =
        temp.newFolder().toPath().resolve("missing_parent").resolve("nested").resolve("trace.pb");
    AtomicLong clock = new AtomicLong(ms(100));
    InternalOptions firstOptions = new InternalOptions();
    firstOptions.perfettoTraceDumpFile = traceFile.toString();
    firstOptions.perfettoTraceLite = true;
    firstOptions.perfettoTraceMinValueMs = 50;

    PerfettoTiming firstTiming = createPerfettoTiming("FirstRun", firstOptions, clock);
    clock.set(ms(500));
    firstTiming.report();
    assertTrue(Files.exists(traceFile));

    clock.set(ms(1000));
    InternalOptions secondOptions = new InternalOptions();
    secondOptions.perfettoTraceDumpFile = traceFile.toString();
    secondOptions.perfettoTraceLite = true;
    secondOptions.perfettoTraceMinValueMs = 50;

    PerfettoTiming secondTiming = createPerfettoTiming("SecondRun", secondOptions, clock);
    clock.set(ms(1010));
    secondTiming.begin("Depth2");
    clock.set(ms(1020));
    secondTiming.begin("Depth3");
    clock.set(ms(1030));
    secondTiming.begin("Depth4");
    clock.set(ms(1400));
    secondTiming.endAll();
    clock.set(ms(1450));
    secondTiming.report();

    MutableTrace trace = decodeTrace(traceFile);
    List<MutableTracePacket> mainDescriptors = findThreadDescriptorPackets(trace, "Main thread");
    assertEquals(1, mainDescriptors.size());
    for (MutableTracePacket packet : trace.getPacket()) {
      MutableTrackEvent event = packet.getTrack_event();
      if (event != null) {
        assertFalse("FirstRun".equals(event.getName()));
      }
    }
    long mainUuid = mainDescriptors.get(0).getTrack_descriptor().getUuid();
    assertEquals(
        Arrays.asList(
            "BEGIN:SecondRun@1000:[R8]",
            "BEGIN:Depth2@1010:[R8]",
            "BEGIN:Depth3@1020:[R8]",
            "BEGIN:Depth4@1030:[R8]",
            "END@1400",
            "END@1400",
            "END@1400",
            "END@1450"),
        formatTrackEvents(trace, mainUuid));
  }

  @Test
  public void testLiteModeNestingBeyondInitialCapacity() throws IOException {
    Path traceFile = temp.newFolder().toPath().resolve("deep.perfetto-trace");
    AtomicLong clock = new AtomicLong(ms(1000));
    InternalOptions options = new InternalOptions();
    options.perfettoTraceDumpFile = traceFile.toString();
    options.perfettoTraceLite = true;
    options.perfettoTraceMinValueMs = 50;

    // With the root slice the stack reaches depth 32, so the leaf and its fast sibling use the
    // pending batch at index 32, which only exists if the batch arrays grow to capacity + 1.
    int nestedFrames = 30;
    List<String> expected = new ArrayList<>();
    expected.add("BEGIN:R8@1000:[R8]");
    PerfettoTiming timing = createPerfettoTiming("R8", options, clock);
    for (int i = 1; i <= nestedFrames; i++) {
      clock.set(ms(1000 + i));
      timing.begin("D" + i);
      expected.add("BEGIN:D" + i + "@" + (1000 + i) + ":[R8]");
    }
    time(clock, timing, "SlowLeaf", ms(1100), ms(1200));
    time(clock, timing, "FastSibling", ms(1200), ms(1210));
    expected.addAll(
        Arrays.asList(
            "BEGIN:SlowLeaf@1100:[R8]", "END@1200", "BEGIN:slices < 50ms@1200:[R8]", "END@1210"));
    for (int i = 0; i < nestedFrames; i++) {
      clock.set(ms(1300 + i));
      timing.end();
      expected.add("END@" + (1300 + i));
    }
    clock.set(ms(1400));
    timing.report();
    expected.add("END@1400");

    MutableTrace trace = decodeTrace(traceFile);
    long mainUuid =
        findThreadDescriptorPackets(trace, "Main thread").get(0).getTrack_descriptor().getUuid();
    assertEquals(expected, formatTrackEvents(trace, mainUuid));
    assertBalancedAndMonotonicPerTrack(trace);
  }

  @Test
  public void testLiteModeDefaultMinValueBatchesShortSlices() throws IOException {
    Path traceFile = temp.newFolder().toPath().resolve("default-min.perfetto-trace");
    AtomicLong clock = new AtomicLong(ms(1000));
    InternalOptions options = new InternalOptions();
    options.perfettoTraceDumpFile = traceFile.toString();
    options.perfettoTraceLite = true;

    PerfettoTiming timing = createPerfettoTiming("R8", options, clock);
    time(clock, timing, "A", ms(1010), ms(1011));
    time(clock, timing, "A", ms(1011), ms(1012));
    clock.set(ms(1020));
    timing.report();

    MutableTrace trace = decodeTrace(traceFile);
    long mainUuid =
        findThreadDescriptorPackets(trace, "Main thread").get(0).getTrack_descriptor().getUuid();
    assertEquals(
        Arrays.asList("BEGIN:R8@1000:[R8]", "BEGIN:slices < 2ms@1010:[R8]", "END@1012", "END@1020"),
        formatTrackEvents(trace, mainUuid));
  }

  @Test
  public void testLiteModeExplicitZeroMinValueOnWorkerThread() throws Exception {
    Path traceFile = temp.newFolder().toPath().resolve("zero-min.perfetto-trace");
    AtomicLong clock = new AtomicLong(ms(1000));
    InternalOptions options = new InternalOptions();
    options.perfettoTraceDumpFile = traceFile.toString();
    options.perfettoTraceLite = true;
    options.perfettoTraceMinValueMs = 0;

    PerfettoTiming timing = createPerfettoTiming("R8", options, clock);
    Thread worker =
        new Thread(
            () -> {
              clock.set(ms(1100));
              Timing workerTiming = timing.createThreadTiming("WorkerRoot", options);
              time(clock, workerTiming, "Child", ms(1101), ms(1102));
              time(clock, workerTiming, "Child", ms(1102), ms(1103));
              clock.set(ms(1104));
              workerTiming.end();
              workerTiming.notifyThreadTimingFinished();
            });
    worker.start();
    worker.join();
    clock.set(ms(1200));
    timing.report();

    MutableTrace trace = decodeTrace(traceFile);
    List<MutableTracePacket> workerDescriptors = findThreadDescriptorPackets(trace, "Worker");
    assertEquals(1, workerDescriptors.size());
    long workerUuid = workerDescriptors.get(0).getTrack_descriptor().getUuid();
    assertEquals(
        Arrays.asList(
            "BEGIN:WorkerRoot@1100:[R8]",
            "BEGIN:Child@1101:[R8]",
            "END@1102",
            "BEGIN:Child@1102:[R8]",
            "END@1103",
            "END@1104"),
        formatTrackEvents(trace, workerUuid));
    assertBalancedAndMonotonicPerTrack(trace);
  }

  @Test
  public void testSystemPropertiesAndEndToEndR8LiteTrace() throws Exception {
    Path traceFile = temp.newFolder().toPath().resolve("r8-e2e.perfetto-trace");
    String prevLite = System.getProperty("com.android.tools.r8.trace.lite");
    String prevMin = System.getProperty("com.android.tools.r8.trace.minvalue_ms");
    try {
      System.clearProperty("com.android.tools.r8.trace.minvalue_ms");
      System.setProperty("com.android.tools.r8.trace.lite", "");
      testForR8(parameters)
          .addInnerClasses(getClass())
          .addKeepMainRule(TestProgram.class)
          .addOptionsModification(
              options -> {
                assertTrue(options.perfettoTraceLite);
                assertEquals(2, options.getPerfettoTraceMinValueMs());
                options.perfettoTraceDumpFile = traceFile.toString();
                options.getTestingOptions().irModifier =
                    (ir, appView) -> {
                      try {
                        Thread.sleep(5);
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                      }
                    };
              })
          .compile()
          .run(parameters.getRuntime(), TestProgram.class)
          .assertSuccessWithOutputLines("Hello, trace!");
    } finally {
      restoreProperty("com.android.tools.r8.trace.lite", prevLite);
      restoreProperty("com.android.tools.r8.trace.minvalue_ms", prevMin);
    }

    MutableTrace trace = decodeTrace(traceFile);
    assertTrue(hasCounterTrack(trace, "Memory"));
    assertTrue(hasCounterTrack(trace, "Uncommitted dex items"));

    List<MutableTracePacket> mainDescriptors = findThreadDescriptorPackets(trace, "Main thread");
    assertEquals(1, mainDescriptors.size());
    long mainUuid = mainDescriptors.get(0).getTrack_descriptor().getUuid();

    int openSlices = 0;
    String firstSliceName = null;
    boolean sawMixedBatchSlice = false;
    for (MutableTracePacket packet : trace.getPacket()) {
      MutableTrackEvent event = packet.getTrack_event();
      if (event != null && event.getTrack_uuid() == mainUuid) {
        if (event.getType() == Type.TYPE_SLICE_BEGIN) {
          if (firstSliceName == null) {
            firstSliceName = event.getName();
          }
          if ("slices < 2ms".equals(event.getName())) {
            sawMixedBatchSlice = true;
          }
          openSlices++;
        } else if (event.getType() == Type.TYPE_SLICE_END) {
          assertTrue(openSlices > 0);
          openSlices--;
        }
      }
    }
    assertEquals("R8 " + Version.LABEL, firstSliceName);
    assertTrue(sawMixedBatchSlice);
    assertEquals(0, openSlices);

    assertFalse(findThreadDescriptorPackets(trace, "Worker").isEmpty());
    assertShortMethodTitles(trace);
    assertBalancedAndMonotonicPerTrack(trace);
  }

  @Test
  public void testLazyTitleSuppliers() throws Exception {
    Path traceFile = temp.newFolder().toPath().resolve("lazy-titles.perfetto-trace");
    InternalOptions options = new InternalOptions();
    options.perfettoTraceDumpFile = traceFile.toString();
    options.perfettoTraceLite = true;
    AtomicLong clock = new AtomicLong(ms(100));
    AtomicInteger supplierCalls = new AtomicInteger();

    PerfettoTiming timing = createPerfettoTiming("Root", options, clock);
    // Dropped slices never evaluate their lazy suppliers.
    timing.begin(
        "Dropped",
        s -> {
          supplierCalls.incrementAndGet();
          return s;
        });
    clock.addAndGet(ms(1));
    timing.end();

    // Emitted slices evaluate their lazy suppliers once.
    Thread worker =
        new Thread(
            () -> {
              Timing workerTiming =
                  timing.createThreadTiming(
                      "WorkerRoot",
                      s -> {
                        supplierCalls.incrementAndGet();
                        return s;
                      },
                      options);
              workerTiming.begin(
                  "EmittedChild",
                  s -> {
                    supplierCalls.incrementAndGet();
                    return s;
                  });
              clock.addAndGet(ms(3));
              workerTiming.end();
              workerTiming.end().notifyThreadTimingFinished();
            });
    worker.start();
    worker.join();

    timing.report();

    assertEquals(2, supplierCalls.get());
    MutableTrace trace = decodeTrace(traceFile);
    long mainUuid =
        findThreadDescriptorPackets(trace, "Main thread").get(0).getTrack_descriptor().getUuid();
    long workerUuid =
        findThreadDescriptorPackets(trace, "Worker").get(0).getTrack_descriptor().getUuid();
    assertEquals(
        Arrays.asList("BEGIN:Root@100:[R8]", "BEGIN:slices < 2ms@100:[R8]", "END@101", "END@104"),
        formatTrackEvents(trace, mainUuid));
    assertEquals(
        Arrays.asList(
            "BEGIN:WorkerRoot@101:[R8]", "BEGIN:EmittedChild@101:[R8]", "END@104", "END@104"),
        formatTrackEvents(trace, workerUuid));
  }

  @Test
  public void testCancellationCheckerKeepsShortMethodTitles() throws Exception {
    Path traceFile = temp.newFolder().toPath().resolve("r8-cancellation.perfetto-trace");
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestProgram.class)
        .addOptionsModification(
            options -> {
              options.perfettoTraceDumpFile = traceFile.toString();
              // Wraps the root PerfettoTiming in a TimingWithCancellation.
              options.cancelCompilationChecker = () -> false;
            })
        .compile();
    assertShortMethodTitles(decodeTrace(traceFile));

    Path delegateTraceFile = temp.newFolder().toPath().resolve("r8-cancellation-delegate.trace");
    InternalOptions options = new InternalOptions();
    options.perfettoTraceDumpFile = delegateTraceFile.toString();
    options.cancelCompilationChecker = () -> false;
    Timing rootTiming =
        new TimingWithCancellation(options, new PerfettoTiming("R8", options, null));
    AtomicInteger workerAssertions = new AtomicInteger();
    Thread worker =
        new Thread(
            () -> {
              Timing threadTiming = rootTiming.createThreadTiming("WorkerTask", options);
              threadTiming.begin("UnclosedChild");
              boolean failed = false;
              try {
                threadTiming.end().notifyThreadTimingFinished();
              } catch (AssertionError expected) {
                failed = true;
              }
              assertTrue(failed);
              threadTiming.begin("AnotherNestedChild");
              threadTiming.endAll().notifyThreadTimingFinished();
              workerAssertions.incrementAndGet();
            });
    worker.start();
    worker.join();
    assertEquals(1, workerAssertions.get());
    rootTiming.report();
  }

  private static void assertShortMethodTitles(MutableTrace trace) {
    Set<String> sliceNames = new HashSet<>();
    for (MutableTracePacket packet : trace.getPacket()) {
      MutableTrackEvent event = packet.getTrack_event();
      if (event != null && event.getType() == Type.TYPE_SLICE_BEGIN) {
        sliceNames.add(event.getName());
      }
    }
    assertTrue(sliceNames.contains("PerfettoTimingTest$TestProgram.main"));
    assertFalse(
        sliceNames.contains(
            "void com.android.tools.r8.utils.timing.PerfettoTimingTest$TestProgram.main"
                + "(java.lang.String[])"));
  }

  private static void restoreProperty(String key, String previousValue) {
    if (previousValue == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, previousValue);
    }
  }

  public static class TestProgram {
    public static void main(String[] args) {
      System.out.println("Hello, trace!");
    }
  }
}
