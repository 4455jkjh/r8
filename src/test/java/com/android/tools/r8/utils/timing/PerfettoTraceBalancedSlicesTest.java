// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.timing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import androidx.tracing.wire.protos.MutableTrace;
import androidx.tracing.wire.protos.MutableTracePacket;
import androidx.tracing.wire.protos.MutableTrackEvent;
import androidx.tracing.wire.protos.MutableTrackEvent.Type;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for per-method thread timings that were never ended in IR conversion. */
@RunWith(Parameterized.class)
public class PerfettoTraceBalancedSlicesTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withMinimumApiLevel().build();
  }

  @Test
  public void test() throws Exception {
    Path traceFile = temp.newFolder().toPath().resolve("trace.perfetto-trace");
    testForR8Compat(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(options -> options.perfettoTraceDumpFile = traceFile.toString())
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
    Map<Long, Integer> openSlicesPerTrack = new HashMap<>();
    MutableTrace trace = MutableTrace.ADAPTER.decode(Files.readAllBytes(traceFile));
    for (MutableTracePacket packet : trace.getPacket()) {
      MutableTrackEvent event = packet.getTrack_event();
      if (event == null) {
        continue;
      }
      if (event.getType() == Type.TYPE_SLICE_BEGIN) {
        openSlicesPerTrack.merge(event.getTrack_uuid(), 1, Integer::sum);
      } else if (event.getType() == Type.TYPE_SLICE_END) {
        openSlicesPerTrack.merge(event.getTrack_uuid(), -1, Integer::sum);
      }
    }
    assertFalse(openSlicesPerTrack.isEmpty());
    openSlicesPerTrack.forEach(
        (track, openSlices) -> assertEquals("Track " + track, 0, (int) openSlices));
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
      if (args.length > 0) {
        // B is never instantiated, so B.foo() is targeted but not live. In compat mode it is
        // therefore converted to throw null code, which is not converted to IR.
        callFoo(null);
      }
    }

    static void callFoo(B b) {
      b.foo();
    }
  }

  static class B {

    void foo() {
      System.out.println("B");
    }
  }
}
