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
 * Test that an unused instance creation is removed when the instance initializer only has side
 * effects due to an argument null check and the argument is known to be non-null, even if the
 * instance initializer assigns instance fields.
 */
@RunWith(Parameterized.class)
public class InstanceInitializerWithArgumentNullCheckSideEffectTest extends TestBase {

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
              // Only the instance creations with a possibly null argument or a used result remain.
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
        .assertSuccessWithOutputLines("NPE", "true");
  }

  static class TestClass {

    public static void main(String[] args) {
      Object nonNull = new Object();
      Object nullIfNoArgs = args.length == 0 ? null : nonNull;
      new A(nonNull);
      try {
        new A(nullIfNoArgs);
        System.out.println("No NPE");
      } catch (NullPointerException e) {
        System.out.println("NPE");
      }
      System.out.println(new A(nonNull).getF() == nonNull);
    }
  }

  @NeverClassInline
  static class A {

    Object f;

    @NeverInline
    A(Object o) {
      o.getClass();
      f = o;
    }

    @NeverInline
    Object getF() {
      return f;
    }
  }
}
