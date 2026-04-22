// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.atomicfieldupdater;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithHolder;
import static java.util.Collections.nCopies;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AtomicFieldUpdaterCatchUsageTest extends AtomicFieldUpdaterBase {

  public AtomicFieldUpdaterCatchUsageTest(TestParameters parameters) {
    super(parameters);
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestParameters.builder().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    Class<TestClass> testClass = TestClass.class;
    boolean isCompareAndSetBackported =
        parameters.isDexRuntime() && parameters.getApiLevel().isLessThan(AndroidApiLevel.Sv2);
    // Non-initializer calls to AtomicXFieldUpdater.
    int methodCount = 9 + (isCompareAndSetBackported ? 1 : 0);
    testForR8(parameters)
        .apply(this::enableAtomicFieldUpdaterWithInfo)
        .addProgramClasses(testClass)
        .addKeepMainRule(testClass)
        .compile()
        .inspectDiagnosticMessagesIf(
            isOptimizationOn(),
            diagnostics -> {
              List<Matcher<Diagnostic>> matchers = new ArrayList<>();
              matchers.addAll(nCopies(3, diagnosticMessage(containsString("Can instrument"))));
              matchers.addAll(
                  nCopies(methodCount, diagnosticMessage(containsString("Can optimize"))));
              // TODO(b/453628974): The field should be removed once nullability analysis is
              //                    more precise.
              // matchers.addAll(nCopies(3, diagnosticMessage(containsString("Can remove"))));
              diagnostics.assertInfosMatch(matchers);
            })
        .inspect(
            inspector -> {
              MethodSubject method = inspector.clazz(testClass).mainMethod();
              DexItemFactory factory = inspector.getFactory();
              if (isOptimizationOn()) {
                assertThat(method, not(invokesMethodWithHolder(AtomicReferenceFieldUpdater.class)));
                assertThat(method, not(invokesMethodWithHolder(AtomicIntegerFieldUpdater.class)));
                assertThat(method, not(invokesMethodWithHolder(AtomicLongFieldUpdater.class)));
                assertEquals(methodCount, countInvokesTo(method, factory.sunMiscUnsafeType));
              } else {
                assertThat(method, not(INVOKES_UNSAFE));
                assertEquals(
                    3 + (isCompareAndSetBackported ? 1 : 0),
                    countInvokesTo(
                        method, factory.javaUtilConcurrentAtomicAtomicReferenceFieldUpdater));
                assertEquals(
                    3,
                    countInvokesTo(
                        method, factory.javaUtilConcurrentAtomicAtomicIntegerFieldUpdater));
                assertEquals(
                    3,
                    countInvokesTo(method, factory.javaUtilConcurrentAtomicAtomicLongFieldUpdater));
              }
            })
        .run(parameters.getRuntime(), testClass)
        .assertSuccessWithOutputLines("Hello!!", "43", "45");
  }

  private long countInvokesTo(MethodSubject method, DexType holder) {
    return method
        .streamInstructions()
        .filter(InstructionSubject::isInvoke)
        .filter(i -> i.getMethod().getHolderType().isIdenticalTo(holder))
        .count();
  }

  public static class TestClass {

    private volatile Object myString;
    private volatile int myInt;
    private volatile long myLong;

    private static final AtomicReferenceFieldUpdater<TestClass, Object> myString$FU;
    private static final AtomicIntegerFieldUpdater<TestClass> myInt$FU;
    private static final AtomicLongFieldUpdater<TestClass> myLong$FU;

    static {
      myString$FU =
          AtomicReferenceFieldUpdater.newUpdater(TestClass.class, Object.class, "myString");
      myInt$FU = AtomicIntegerFieldUpdater.newUpdater(TestClass.class, "myInt");
      myLong$FU = AtomicLongFieldUpdater.newUpdater(TestClass.class, "myLong");
    }

    public TestClass() {
      super();
      myString = "empty";
      myInt = 0;
      myLong = 0L;
    }

    public static void main(String[] args) {
      TestClass instance = new TestClass();
      try {
        // Reference.
        myString$FU.set(instance, "Hello");
        myString$FU.compareAndSet(instance, "Hello", "Hello!!");
        System.out.println(myString$FU.get(instance));

        // Integer.
        myInt$FU.set(instance, 42);
        myInt$FU.compareAndSet(instance, 42, 43);
        System.out.println(myInt$FU.get(instance));

        // Long.
        myLong$FU.set(instance, 44L);
        myLong$FU.compareAndSet(instance, 44L, 45L);
        System.out.println(myLong$FU.get(instance));
      } catch (Exception e) {
        // The try block never throws. This is here to test the rewriting with catch handlers.
        throw new RuntimeException(e);
      }
    }
  }
}
