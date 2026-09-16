// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.membervaluepropagation.readbeforewrite;

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
public class FieldReadBeforeWriteWithEscapedThrowTest extends TestBase {

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
        .assertSuccessWithOutputLines("ALLOW", "DENY");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("ALLOW", "DENY");
  }

  @NoHorizontalClassMerging
  static class Guard {
    static Guard PENDING;
    String secret;

    @NeverInline
    Guard(RuntimeException err) {
      PENDING = this;
      if (err != null) {
        throw err;
      }
      this.secret = "GRANTED";
    }
  }

  static class Main {

    @NeverInline
    static void run(RuntimeException e) {
      try {
        new Guard(e);
      } catch (RuntimeException ignored) {
      }
      if (Guard.PENDING != null && Guard.PENDING.secret == null) {
        System.out.println("DENY");
      } else {
        System.out.println("ALLOW");
      }
    }

    public static void main(String[] args) {
      run(null);
      run(new RuntimeException());
    }
  }
}
