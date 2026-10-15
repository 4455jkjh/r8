// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.sideeffect;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.KeepUnusedReturnValue;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Test that an unused call to a method that only has side effects due to an argument null check is
 * removed when the argument is known to be non-null, even if the method computes a return value.
 */
@RunWith(Parameterized.class)
public class MethodWithArgumentNullCheckSideEffectTest extends TestBase {

  @Parameterized.Parameter(0)
  public TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(MethodWithArgumentNullCheckSideEffectTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableKeepUnusedReturnValueAnnotations()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject testClassSubject = inspector.clazz(TestClass.class);
              MethodSubject hashSubject = testClassSubject.uniqueMethodWithOriginalName("hash");
              assertThat(hashSubject, isPresent());
              // Only the call with a possibly null argument remains.
              assertEquals(
                  1,
                  testClassSubject
                      .mainMethod()
                      .streamInstructions()
                      .filter(CodeMatchers.isInvokeWithTarget(hashSubject))
                      .count());
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("NPE");
  }

  static class TestClass {

    public static void main(String[] args) {
      Object nonNull = new Object();
      Object nullIfNoArgs = args.length == 0 ? null : nonNull;
      hash(nonNull, args.length);
      try {
        hash(nullIfNoArgs, args.length);
      } catch (NullPointerException e) {
        System.out.println("NPE");
      }
    }

    @KeepUnusedReturnValue
    @NeverInline
    static int hash(Object o, int seed) {
      o.getClass();
      return seed * 31 + 7;
    }
  }
}
