// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.phis;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EffectivelyTrivialPhiPrematureClassInitTest extends TestBase {

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
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("main:b:C:false");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableNeverClassInliningAnnotations()
        .applyIf(parameters.isDexRuntime(), b -> b.setMinApi(parameters))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("main:b:C:false");
  }

  public static class Main {

    public static void main(String[] a) {
      System.out.print("main:");
      C c;
      if (a.length > 0) {
        System.out.print("a:");
        c = C.INSTANCE;
      } else {
        System.out.print("b:");
        c = C.INSTANCE;
      }
      System.out.println(c == null);
    }
  }

  @NeverClassInline
  public static class C {
    static final C INSTANCE = new C();

    static {
      System.out.print("C:");
    }
  }
}
