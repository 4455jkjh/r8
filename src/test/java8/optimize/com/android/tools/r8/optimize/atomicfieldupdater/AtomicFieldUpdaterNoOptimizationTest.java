// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.atomicfieldupdater;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AtomicFieldUpdaterNoOptimizationTest extends AtomicFieldUpdaterBase {

  public AtomicFieldUpdaterNoOptimizationTest(TestParameters parameters) {
    super(parameters);
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestParameters.builder().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    Class<TestClass> testClass = TestClass.class;
    testForR8(parameters)
        .apply(this::enableAtomicFieldUpdaterWithInfo)
        .addProgramClasses(testClass)
        .addKeepMainRule(testClass)
        .compile()
        .inspectDiagnosticMessagesIf(
            isOptimizationOn(),
            diagnostics ->
                diagnostics.assertInfosMatch(
                    diagnosticMessage(containsString("Can instrument")),
                    diagnosticMessage(containsString("Cannot remove"))))
        .inspect(
            inspector -> {
              // Check that the synthetic unsafe class is not present.
              assertEquals(1, inspector.allClasses().size());
              ClassSubject clazz = inspector.clazz(testClass);
              // Check that offset field is not present.
              for (FieldSubject field : clazz.allFields()) {
                assertFalse("field must not have type: long", field.getType().is("long"));
              }
              // Check that the initialization code for the offset field has been removed.
              MethodSubject method = clazz.clinit();
              assertThat(method, not(INVOKES_UNSAFE));
            })
        .run(parameters.getRuntime(), testClass)
        .assertSuccessWithOutputLines("true");
  }

  // Corresponding to simple kotlin usage of `atomic("Hello")` via atomicfu.
  public static class TestClass {

    private volatile Object myString;

    private static final AtomicReferenceFieldUpdater<TestClass, Object> myString$FU;

    static {
      myString$FU =
          AtomicReferenceFieldUpdater.newUpdater(TestClass.class, Object.class, "myString");
    }

    public TestClass() {
      super();
      myString = "Hello";
    }

    public static void main(String[] args) {
      AtomicReferenceFieldUpdater<TestClass, Object> other;
      if (System.currentTimeMillis() > 0) {
        other = myString$FU;
      } else {
        other = null;
      }
      // 'equals' is used since it is never optimized.
      // 'other' is used to avoid general compile time evaluation.
      System.out.println(myString$FU.equals(other));
    }
  }
}
