// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isImplementing;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPackagePrivate;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPublic;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NonPublicOverrideOfPublicMethodAfterAbstractClassMergingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector.assertIsCompleteMergeGroup(A.class, X.class).assertNoOtherClassesMerged())
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(
            inspector -> {
              ClassSubject iClassSubject = inspector.clazz(I.class);
              assertThat(iClassSubject, isPresent());
              assertThat(iClassSubject.uniqueMethodWithName("m"), allOf(isPresent(), isPublic()));

              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());
              assertThat(aClassSubject, isImplementing(iClassSubject));
              assertThat(
                  aClassSubject.uniqueMethodWithName("m"), allOf(isPresent(), isPackagePrivate()));
            })
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.isCfRuntime(),
            runResult -> runResult.assertSuccessWithOutputLines("A.m()", "Y.m()"),
            runResult ->
                runResult.applyIf(
                    parameters.getDexRuntimeVersion().isDalvik(),
                    ignore ->
                        runResult.assertFailureWithErrorThatThrows(NoClassDefFoundError.class),
                    ignore ->
                        runResult.assertFailureWithErrorThatThrows(IllegalAccessError.class)));
  }

  static class Main {

    public static void main(String[] args) {
      new B().m();
      (System.currentTimeMillis() > 0 ? new Y() : new Z()).m();
    }
  }

  interface I {

    void m();
  }

  @NoVerticalClassMerging
  abstract static class A {

    @NeverInline
    void m() {
      System.out.println("A.m()");
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class B extends A {}

  @NoVerticalClassMerging
  abstract static class X implements I {}

  @NoHorizontalClassMerging
  static class Y extends X {

    @Override
    public void m() {
      System.out.println("Y.m()");
    }
  }

  @NoHorizontalClassMerging
  static class Z implements I {

    @Override
    public void m() {
      System.out.println("Z.m()");
    }
  }
}
