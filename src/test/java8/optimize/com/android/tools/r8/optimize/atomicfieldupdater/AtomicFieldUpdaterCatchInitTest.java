// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.atomicfieldupdater;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AtomicFieldUpdaterCatchInitTest extends AtomicFieldUpdaterBase {

  public AtomicFieldUpdaterCatchInitTest(TestParameters parameters) {
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
                    diagnosticMessage(containsString("Cannot instrument")),
                    diagnosticMessage(containsString("Cannot instrument")),
                    diagnosticMessage(containsString("Cannot instrument"))))
        .inspect(
            inspector -> {
              MethodSubject method = inspector.clazz(testClass).mainMethod();
              assertThat(method, not(INVOKES_UNSAFE));
              MethodSubject classInitializer = inspector.clazz(testClass).clinit();
              assertThat(classInitializer, not(INVOKES_UNSAFE));
            })
        .run(parameters.getRuntime(), testClass)
        .assertSuccessWithOutputLines("Hello", "42", "42");
  }

  // Corresponding to simple kotlin usage of `atomic(..)` via atomicfu.
  public static class TestClass {

    private volatile Object myString;
    private volatile int myInt;
    private volatile long myLong;

    private static final AtomicReferenceFieldUpdater<TestClass, Object> myString$FU;
    private static final AtomicIntegerFieldUpdater<TestClass> myInt$FU;
    private static final AtomicLongFieldUpdater<TestClass> myLong$FU;

    static {
      try {
        myString$FU =
            AtomicReferenceFieldUpdater.newUpdater(TestClass.class, Object.class, "myString");
        myInt$FU = AtomicIntegerFieldUpdater.newUpdater(TestClass.class, "myInt");
        myLong$FU = AtomicLongFieldUpdater.newUpdater(TestClass.class, "myLong");
      } catch (Exception e) {
        // The try block never throws. This is here to test the rewriting with catch handlers.
        throw new RuntimeException(e);
      }
    }

    public TestClass() {
      super();
      myString = "Hello";
      myInt = 42;
      myLong = 42L;
    }

    public static void main(String[] args) {
      TestClass instance = new TestClass();
      System.out.println(myString$FU.get(instance));
      System.out.println(myInt$FU.get(instance));
      System.out.println(myLong$FU.get(instance));
    }
  }
}
