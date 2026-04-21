// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.atomicfieldupdater.longtype;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.optimize.atomicfieldupdater.AtomicFieldUpdaterBase;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AtomicFieldUpdaterGetTest extends AtomicFieldUpdaterBase {

  public AtomicFieldUpdaterGetTest(TestParameters parameters) {
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
                    diagnosticMessage(containsString("Can optimize"))
                    // TODO(b/453628974): The field should be removed once nullability analysis is
                    //                    more precise.
                    // diagnosticMessage(containsString("Can remove"))
                    ))
        .inspect(
            inspector -> {
              MethodSubject method = inspector.clazz(testClass).mainMethod();
              if (isOptimizationOn()) {
                assertThat(
                    method,
                    CodeMatchers.invokesMethodWithHolderAndName(
                        "sun.misc.Unsafe", "getLongVolatile"));
              } else {
                assertThat(
                    method,
                    CodeMatchers.invokesMethodWithHolderAndName(
                        AtomicLongFieldUpdater.class, "get"));
              }
            })
        .run(parameters.getRuntime(), testClass)
        .assertSuccessWithOutputLines("123");
  }

  // Corresponding to simple kotlin usage of `atomic(123L)` via atomicfu.
  public static class TestClass {

    private volatile long myLong;

    private static final AtomicLongFieldUpdater<TestClass> myLong$FU;

    static {
      myLong$FU = AtomicLongFieldUpdater.newUpdater(TestClass.class, "myLong");
    }

    public TestClass() {
      super();
      myLong = 123L;
    }

    public static void main(String[] args) {
      TestClass instance = new TestClass();
      System.out.println(myLong$FU.get(instance));
    }
  }
}
