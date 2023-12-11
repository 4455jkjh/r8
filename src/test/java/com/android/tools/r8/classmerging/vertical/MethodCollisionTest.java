// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.vertical;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MethodCollisionTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addVerticallyMergedClassesInspector(
            inspector -> inspector.assertMergedIntoSubtype(A.class, C.class))
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class);
  }

  public static class Main {

    public static void main(String[] args) {
      B b = new B();
      b.m();

      D d = new D();
      d.m();

      // Ensure that the instantiations are not dead code eliminated.
      escape(b);
      escape(d);
    }

    @NeverInline
    static void escape(Object o) {
      if (System.currentTimeMillis() < 0) {
        System.out.println(o);
      }
    }
  }

  @NoHorizontalClassMerging
  public static class A {

    // After class merging, this method will have the same signature as the method B.m,
    // unless we handle the collision.
    private A m() {
      System.out.println("A.m");
      return null;
    }

    public void invokeM() {
      m();
    }
  }

  public static class B extends A {

    private B m() {
      System.out.println("B.m");
      invokeM();
      return null;
    }
  }

  @NoHorizontalClassMerging
  public static class C {

    // After class merging, this method will have the same signature as the method D.m,
    // unless we handle the collision.
    public C m() {
      System.out.println("C.m");
      return null;
    }
  }

  public static class D extends C {

    public D m() {
      System.out.println("D.m");
      super.m();
      return null;
    }
  }
}
