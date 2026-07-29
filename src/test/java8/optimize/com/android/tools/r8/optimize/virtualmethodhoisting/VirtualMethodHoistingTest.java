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

import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VirtualMethodHoistingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableNoHorizontalClassMergingAnnotations()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("1", "2");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject baseClass = inspector.clazz(Base.class);
    assertThat(baseClass, isPresent());

    // Base#method should no longer be abstract
    MethodSubject baseMethod = baseClass.uniqueMethodWithOriginalName("method");
    assertThat(baseMethod, isPresent());
    assertThat(baseMethod, not(isAbstract()));
    assertTrue(
        baseMethod.streamInstructions().anyMatch(instruction -> instruction.isConstNumber(1)));

    // A#method should be hoisted to Base, so A should no longer have it.
    ClassSubject aClass = inspector.clazz(A.class);
    assertThat(aClass, isPresent());
    assertThat(aClass.uniqueMethodWithOriginalName("method"), isAbsent());

    // B#method should still be present.
    ClassSubject bClass = inspector.clazz(B.class);
    assertThat(bClass, isPresent());
    assertThat(bClass.uniqueMethodWithOriginalName("method"), isPresent());
  }

  static class Main {

    public static void main(String[] args) {
      Base a = System.currentTimeMillis() > 0 ? new A() : new B();
      Base b = System.currentTimeMillis() > 0 ? new B() : new A();
      System.out.println(a.method());
      System.out.println(b.method());
    }
  }

  public abstract static class Base {
    public abstract int method();
  }

  @NoHorizontalClassMerging
  public static class A extends Base {

    @Override
    public int method() {
      return 1;
    }
  }

  @NoHorizontalClassMerging
  public static class B extends Base {

    @Override
    public int method() {
      return 2;
    }
  }
}
