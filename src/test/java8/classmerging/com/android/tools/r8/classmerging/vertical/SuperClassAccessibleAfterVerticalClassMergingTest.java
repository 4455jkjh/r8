// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.vertical;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.codeinspector.VerticallyMergedClassesInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SuperClassAccessibleAfterVerticalClassMergingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().withoutCollapsedDexRuntimes().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepClassRules(A.class)
        .addRepackagingInspector(
            inspector -> inspector.assertIsRepackaged(Reference.classFromClass(C.class)))
        .addVerticallyMergedClassesInspector(
            VerticallyMergedClassesInspector::assertNoClassesMerged)
        .enableInliningAnnotations()
        .compile()
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(A.class), isPresent());
              assertThat(inspector.clazz(B.class), isAbsent());
              assertThat(inspector.clazz(C.class), isPresent());
            })
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/580760674): Should succeed with "C.m()".
        .assertFailureWithErrorThatThrows(
            parameters.isDexRuntime() && parameters.getDexRuntimeVersion().isDalvik()
                ? NoClassDefFoundError.class
                : IllegalAccessError.class);
  }

  static class Main {

    static boolean never() {
      return false;
    }

    public static void main(String[] args) {
      if (never()) {
        System.out.println(B.create());
      }
      C.m();
    }
  }

  static class A {}

  public static class B extends A {

    public static Object create() {
      return new B();
    }
  }

  public static class C extends B {

    @NeverInline
    public static void m() {
      System.out.println("C.m()");
    }
  }
}
