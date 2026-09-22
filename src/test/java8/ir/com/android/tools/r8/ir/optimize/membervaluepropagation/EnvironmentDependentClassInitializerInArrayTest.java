// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.membervaluepropagation;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnvironmentDependentClassInitializerInArrayTest extends TestBase {

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
        .run(parameters.getRuntime(), App.class)
        .assertSuccessWithOutputLines("0");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(App.class)
        .enableNeverClassInliningAnnotations()
        .run(parameters.getRuntime(), App.class)
        // TODO(b/557274033): C.<clinit> should not be postponed.
        .assertSuccessWithOutputLines(
            parameters.isDexRuntime()
                    && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.T)
                ? "0"
                : "9");
  }

  public static class D {
    public static int level;
  }

  @NeverClassInline
  public static class Holder {
    final int v;

    Holder(int v) {
      this.v = v;
    }
  }

  public static class C {
    static final Holder[] H;

    static {
      Holder[] h = new Holder[2];
      h[0] = new Holder(D.level);
      H = h;
    }

    static void touch() {}
  }

  public static class App {

    public static void main(String[] args) {
      C.touch();
      D.level = 9;
      System.out.println(C.H[0].v);
    }
  }
}
