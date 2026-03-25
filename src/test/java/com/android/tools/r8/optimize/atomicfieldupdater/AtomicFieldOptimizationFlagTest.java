// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.atomicfieldupdater;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AtomicFieldOptimizationFlagTest extends AtomicFieldUpdaterBase {

  public AtomicFieldOptimizationFlagTest(TestParameters parameters) {
    super(parameters);
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestParameters.builder().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    Class<?> testClass = TestClass.class;
    testForR8(parameters)
        .addOptionsModification(
            options -> {
              assertTrue(options.enableAtomicUpdaterOptimization);
              options.enableAtomicUpdaterOptimization = false;
            })
        .addProgramClasses(testClass)
        .addKeepMainRule(testClass)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject method = inspector.clazz(testClass).mainMethod();
              assertThat(method, not(INVOKES_UNSAFE));
              assertThat(
                  method, CodeMatchers.invokesMethodWithHolder(AtomicReferenceFieldUpdater.class));
            });
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
      System.out.println(myString$FU.get(new TestClass()));
    }
  }
}
