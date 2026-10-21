// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.retrace.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.ProguardMappingSupplier;
import com.android.tools.r8.retrace.RetraceFrameElement;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.RetraceThrownExceptionElement;
import com.android.tools.r8.retrace.RetracedMethodReference;
import com.android.tools.r8.retrace.RetracedSingleFrame;
import com.android.tools.r8.retrace.Retracer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RetraceApiStackTraceContextSerializeTest extends RetraceApiTestBase {

  public RetraceApiStackTraceContextSerializeTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  protected Class<? extends RetraceApiBinaryTest> binaryTestClass() {
    return ApiTest.class;
  }

  public static class ApiTest implements RetraceApiBinaryTest {

    private final String mapping =
        "# { id: 'com.android.tools.r8.mapping', version: '2.0' }\n"
            + "outline.Class -> a:\n"
            + "  1:2:int outline():0:0 -> a\n"
            + "# { 'id':'com.android.tools.r8.outline' }\n"
            + "some.Class -> b:\n"
            + "  1:1:void foo.bar.Baz.qux():42:42 -> s\n"
            + "  4:4:int foo.bar.baz.outlineCaller(int):98:98 -> s\n"
            + "  4:4:int outlineCaller(int):24 -> s\n"
            + "  27:27:int outlineCaller(int):0:0 -> s\n"
            + "# { 'id':'com.android.tools.r8.outlineCallsite', "
            + "    'positions': { '1': 4, '2': 5 } }\n"
            + "  40:40:void other.Class.inlinee():23:23 -> a\n"
            + "  40:40:void caller(other.Class):7 -> a\n"
            + "  # { id: 'com.android.tools.r8.rewriteFrame', "
            + "      conditions: ['throws(Ljava/lang/NullPointerException;)'], "
            + "      actions: ['removeInnerFrames(1)']"
            + "    }\n";

    @Test
    public void testEmpty() {
      RetraceStackTraceContext empty = RetraceStackTraceContext.empty();
      byte[] serialized = empty.serialize();
      assertEquals(0, serialized.length);
      assertEquals(empty, RetraceStackTraceContext.deserialize(serialized));
    }

    @Test
    public void testOutlineAndThrownException() {
      ProguardMappingSupplier mappingSupplier =
          ProguardMappingSupplier.builder()
              .setProguardMapProducer(ProguardMapProducer.fromString(mapping))
              .build();
      Retracer retracer = mappingSupplier.createRetracer(new DiagnosticsHandler() {});

      // Test serializing and deserializing outline context.
      List<RetraceFrameElement> outlineRetraced =
          retracer
              .retraceFrame(
                  RetraceStackTraceContext.empty(),
                  OptionalInt.of(1),
                  Reference.methodFromDescriptor("La;", "a", "()I"))
              .stream()
              .collect(Collectors.toList());
      assertEquals(1, outlineRetraced.size());
      RetraceStackTraceContext outlineContext =
          outlineRetraced.get(0).getRetraceStackTraceContext();
      RetraceStackTraceContext deserializedOutlineContext =
          RetraceStackTraceContext.deserialize(outlineContext.serialize());
      assertEquals(outlineContext, deserializedOutlineContext);

      List<RetraceFrameElement> retraceOutlineCallee =
          retracer
              .retraceFrame(
                  deserializedOutlineContext,
                  OptionalInt.of(27),
                  Reference.methodFromDescriptor("Lb;", "s", "(I)I"))
              .stream()
              .collect(Collectors.toList());
      assertEquals(1, retraceOutlineCallee.size());
      List<RetracedMethodReference> outlineCallSiteFrames =
          retraceOutlineCallee.get(0).stream()
              .map(RetracedSingleFrame::getMethodReference)
              .collect(Collectors.toList());
      assertEquals(2, outlineCallSiteFrames.size());
      assertEquals(98, outlineCallSiteFrames.get(0).getOriginalPositionOrDefault(27));
      assertEquals(24, outlineCallSiteFrames.get(1).getOriginalPositionOrDefault(27));

      // Test serializing and deserializing thrown exception context.
      List<RetraceThrownExceptionElement> npeRetraced =
          retracer
              .retraceThrownException(Reference.classFromClass(NullPointerException.class))
              .stream()
              .collect(Collectors.toList());
      assertEquals(1, npeRetraced.size());
      RetraceStackTraceContext throwingContext = npeRetraced.get(0).getContext();
      RetraceStackTraceContext deserializedThrowingContext =
          RetraceStackTraceContext.deserialize(throwingContext.serialize());
      assertEquals(throwingContext, deserializedThrowingContext);

      List<RetraceFrameElement> retraceFrameElements =
          retracer.retraceClass(Reference.classFromDescriptor("Lb;")).stream()
              .flatMap(
                  element ->
                      element
                          .lookupFrame(deserializedThrowingContext, OptionalInt.of(40), "a")
                          .stream())
              .collect(Collectors.toList());
      assertEquals(1, retraceFrameElements.size());
      RetraceFrameElement retraceFrameElement = retraceFrameElements.get(0);
      Map<Integer, RetracedMethodReference> results = new LinkedHashMap<>();
      retraceFrameElement
          .streamRewritten(deserializedThrowingContext)
          .forEach(
              frame -> {
                RetracedMethodReference existingValue =
                    results.put(frame.getIndex(), frame.getMethodReference());
                assertNull(existingValue);
              });
      assertEquals(1, results.size());
      assertEquals(7, results.get(0).getOriginalPositionOrDefault(40));
      assertEquals("caller", results.get(0).getMethodName());
    }
  }
}
