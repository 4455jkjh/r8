// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.vertical;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoParameterTypeStrengthening;
import com.android.tools.r8.NoUnusedInterfaceRemoval;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VerticalClassMergerKeepInfoCollisionTest extends TestBase {

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
        .addKeepRules(
            "-keep,allowoptimization,allowshrinking class " + I.class.getTypeName() + " {",
            "  void m();",
            "}",
            "-keep class " + B.class.getTypeName() + " {",
            "  void m();",
            "}")
        .addVerticallyMergedClassesInspector(
            inspector -> inspector.assertMergedIntoSubtype(I.class))
        .enableInliningAnnotations()
        .enableNoParameterTypeStrengtheningAnnotations()
        .enableNoUnusedInterfaceRemovalAnnotations()
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class Main {

    public static void main(String[] args) {
      call(new B());
    }

    @NeverInline
    @NoParameterTypeStrengthening
    static void call(I i) {
      i.m();
    }
  }

  @NoUnusedInterfaceRemoval
  interface I {

    void m();
  }

  static class B implements I {

    @Override
    public void m() {
      System.out.println("Hello, world!");
    }
  }
}
