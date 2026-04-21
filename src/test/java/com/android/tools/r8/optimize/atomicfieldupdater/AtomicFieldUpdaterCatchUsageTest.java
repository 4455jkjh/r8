// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.atomicfieldupdater;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
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
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
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
        isOptimizationOn() && parameters.getApiLevel().isLessThan(AndroidApiLevel.Sv2);
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
                  nCopies(
                      13 + (isCompareAndSetBackported ? 1 : 0),
                      diagnosticMessage(containsString("Cannot optimize"))));
              matchers.addAll(nCopies(3, diagnosticMessage(containsString("Cannot remove"))));
              diagnostics.assertInfosMatch(matchers);
            })
        .inspect(
            inspector -> {
              MethodSubject method = inspector.clazz(testClass).mainMethod();
              assertThat(method, not(INVOKES_UNSAFE));
              if (isOptimizationOn()) {
                DexItemFactory factory = inspector.getFactory();
                assertEquals(
                    isCompareAndSetBackported ? 6 : 5,
                    countInvokesTo(
                        method, factory.javaUtilConcurrentAtomicAtomicReferenceFieldUpdater));
                assertEquals(
                    4,
                    countInvokesTo(
                        method, factory.javaUtilConcurrentAtomicAtomicIntegerFieldUpdater));
                assertEquals(
                    4,
                    countInvokesTo(method, factory.javaUtilConcurrentAtomicAtomicLongFieldUpdater));
              } else {
                assertThat(
                    method,
                    CodeMatchers.invokesMethodWithHolder(AtomicReferenceFieldUpdater.class));
                assertThat(
                    method, CodeMatchers.invokesMethodWithHolder(AtomicIntegerFieldUpdater.class));
                assertThat(
                    method, CodeMatchers.invokesMethodWithHolder(AtomicLongFieldUpdater.class));
              }
            })
        .run(parameters.getRuntime(), testClass)
        .assertSuccessWithOutputLines("Hello!!", "1", "1");
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
      myString = "Hello";
      myInt = 0;
      myLong = 0L;
    }

    public static void main(String[] args) {
      TestClass instance = new TestClass();
      try {
        // Reference.
        Object old = myString$FU.getAndSet(instance, "World");
        myString$FU.compareAndSet(instance, "World", old.toString() + "!!");
        myString$FU.set(instance, myString$FU.get(instance));
        System.out.println(myString$FU.get(instance));

        // Integer.
        int oldInt = myInt$FU.getAndSet(instance, -1);
        myInt$FU.set(instance, 42);
        myInt$FU.compareAndSet(instance, 42, oldInt + 1);
        System.out.println(myInt$FU.get(instance));

        // Long.
        long oldLong = myLong$FU.getAndSet(instance, -1L);
        myLong$FU.set(instance, 42L);
        myLong$FU.compareAndSet(instance, 42L, oldLong + 1L);
        System.out.println(myLong$FU.get(instance));
      } catch (Exception e) {
        // The try block never throws. This is here to test the rewriting with catch handlers.
        throw new RuntimeException(e);
      }
    }
  }
}
