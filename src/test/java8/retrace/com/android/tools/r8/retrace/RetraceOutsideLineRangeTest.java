// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.NopDiagnosticsHandler;
import com.android.tools.r8.utils.internal.StringUtils;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetraceOutsideLineRangeTest extends TestBase {

  private static final ClassReference fooClass = Reference.classFromTypeName("foo.Foo");
  private static final ClassReference aClass = Reference.classFromTypeName("a");
  private static final ClassReference barClass = Reference.classFromTypeName("bar.Bar");
  private static final ClassReference bClass = Reference.classFromTypeName("b");

  private static final String mapping =
      StringUtils.lines(
          "foo.Foo -> a:",
          "  void method1():42:42 -> a",
          "bar.Bar -> b:",
          "  28:28:void foo.bar.inlinee():92:92 -> a",
          "  1:3:void method2():11:13 -> a",
          "  4:4:void method2():10:10 -> a",
          "  5:5:void method2():14 -> a");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void test() {
    Retracer retracer =
        Retracer.createDefault(
            ProguardMapProducer.fromString(mapping), new NopDiagnosticsHandler());
    retraceClassMethodAndPosition(retracer, aClass, fooClass, 1, 1, false);
    retraceClassMethodAndPosition(retracer, bClass, barClass, 2, 1, true);
  }

  private void retraceClassMethodAndPosition(
      Retracer retracer,
      ClassReference renamedClass,
      ClassReference originalClass,
      int methodCount,
      int frameCount,
      boolean isEmpty) {
    // Retrace class.
    List<RetraceClassElement> classResult =
        retracer.retraceClass(renamedClass).stream().collect(Collectors.toList());
    assertEquals(1, classResult.size());
    RetraceClassElement retraceClassResult = classResult.get(0);
    assertEquals(originalClass, retraceClassResult.getRetracedClass().getClassReference());

    // Retrace method "a".
    RetraceMethodResult retraceMethodResult = retraceClassResult.lookupMethod("a");
    List<RetraceMethodElement> classMethodResults =
        retraceMethodResult.stream().collect(Collectors.toList());
    assertEquals(methodCount, classMethodResults.size());

    // Narrow by line 6. Note that the mapping for Bar does not have any range matching line 6.
    RetraceFrameResult retraceFrameResult =
        retraceMethodResult.narrowByPosition(RetraceStackTraceContext.empty(), OptionalInt.of(6));
    assertEquals(isEmpty, retraceFrameResult.isEmpty());
    if (isEmpty) {
      // TODO(b/571653213): Should not fail with AssertionError/IndexOutOfBoundsException.
      assertThrows(AssertionError.class, retraceFrameResult::isAmbiguous);
    } else {
      assertFalse(retraceFrameResult.isAmbiguous());
    }
    List<RetraceFrameElement> classResultFrames =
        retraceFrameResult.stream().collect(Collectors.toList());
    assertEquals(frameCount, classResultFrames.size());
  }
}
