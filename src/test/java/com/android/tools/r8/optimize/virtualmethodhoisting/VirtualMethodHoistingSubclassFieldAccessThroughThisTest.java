// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.virtualmethodhoisting;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbstract;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VirtualMethodHoistingSubclassFieldAccessThroughThisTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(Main.class, Base.class, A.class, B.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("1", "2");
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addProgramClasses(Main.class, Base.class, A.class, B.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .addVirtualMethodHoisterInspector(
            inspector ->
                inspector
                    .assertHoisted(B.class.getMethod("method"))
                    .assertMethodCheckedButNotHoisted(A.class.getMethod("method")))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("1", "2")
        .inspect(
            inspector -> {
              ClassSubject baseClass = inspector.clazz(Base.class);
              assertThat(baseClass, isPresent());

              // Base#method should no longer be abstract (it should have hoisted B#method).
              MethodSubject baseMethod = baseClass.uniqueMethodWithOriginalName("method");
              assertThat(baseMethod, isPresent());
              assertThat(baseMethod, not(isAbstract()));
              assertTrue(
                  baseMethod
                      .streamInstructions()
                      .anyMatch(instruction -> instruction.isConstNumber(2)));

              // B should not have the method (it inherits from Base).
              ClassSubject bClass = inspector.clazz(B.class);
              assertThat(bClass, isPresent());
              assertThat(bClass.uniqueMethodWithOriginalName("method"), isAbsent());

              // A#method should still be present.
              ClassSubject aClass = inspector.clazz(A.class);
              assertThat(aClass, isPresent());
              assertThat(aClass.uniqueMethodWithOriginalName("method"), isPresent());
            });
  }

  public static class Main {

    public static void main(String[] args) {
      Base a = System.currentTimeMillis() > 0 ? new A() : new B();
      Base b = System.currentTimeMillis() > 0 ? new B() : new A();
      System.out.println(a.method());
      System.out.println(b.method());
    }
  }

  @NoVerticalClassMerging
  abstract static class Base {

    @NeverInline
    public abstract int method();
  }

  @NoVerticalClassMerging
  @NoHorizontalClassMerging
  @NeverClassInline
  public static class A extends Base {
    private final A prev = System.currentTimeMillis() > 0 ? null : new A();
    int x = System.currentTimeMillis() > 0 ? 1 : 2;

    @Override
    @NeverInline
    public int method() {
      A a = this;
      while (true) {
        if (a.prev == null) {
          return a.x;
        } else {
          a = a.prev;
        }
      }
    }
  }

  @NoVerticalClassMerging
  @NoHorizontalClassMerging
  @NeverClassInline
  public static class B extends Base {
    int x = 2;

    @Override
    @NeverInline
    @NeverPropagateValue
    public int method() {
      return x;
    }
  }
}
