// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.sideeffect;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Regression test for an instance initializer with more argument null checks than the maximum size
 * of a conjunction constraint, in which case the constraint under which the initializer has no side
 * effects is never satisfied.
 */
@RunWith(Parameterized.class)
public class InstanceInitializerWithManyArgumentNullChecksTest extends TestBase {

  @Parameterized.Parameter(0)
  public TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());
              // Both instance creations remain.
              assertEquals(
                  2,
                  inspector
                      .clazz(TestClass.class)
                      .mainMethod()
                      .streamInstructions()
                      .filter(
                          instruction -> instruction.isNewInstance(aClassSubject.getFinalName()))
                      .count());
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("NPE");
  }

  static class TestClass {

    public static void main(String[] args) {
      Object o = new Object();
      Object nullIfNoArgs = args.length == 0 ? null : o;
      new A(o, o, o, o);
      try {
        new A(nullIfNoArgs, nullIfNoArgs, nullIfNoArgs, nullIfNoArgs);
        System.out.println("No NPE");
      } catch (NullPointerException e) {
        System.out.println("NPE");
      }
    }
  }

  @NeverClassInline
  static class A {

    @NeverInline
    A(Object o1, Object o2, Object o3, Object o4) {
      o1.getClass();
      o2.getClass();
      o3.getClass();
      o4.getClass();
    }
  }
}
