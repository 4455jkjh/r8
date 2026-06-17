// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.internal.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProtectApiSurfaceHorizontalClassMergingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean protectApiSurface;

  @Parameters(name = "{0}, protect: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(B.class)
        .addHorizontallyMergedClassesInspector(
            inspector -> {
              if (!protectApiSurface && parameters.isDexRuntime()) {
                inspector.assertIsCompleteMergeGroup(A.class, C.class);
              }
              inspector.assertNoOtherClassesMerged();
            })
        .apply(b -> b.getBuilder().setProtectApiSurface(protectApiSurface))
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "A", "B", "C");
  }

  @NoVerticalClassMerging
  public static class A {

    public A() {
      System.out.println("A");
    }
  }

  // Kept.
  public static class B extends A {

    public B() {
      System.out.println("B");
    }
  }

  @NeverClassInline
  public static class C {

    @NeverInline
    public void m() {
      System.out.println("C");
    }
  }

  public static class Main {
    public static void main(String[] args) {
      new A();
      new B();
      new C().m();
    }
  }
}
