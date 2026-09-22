// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classinitialization;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InitClassEliminationWithThrowingArrayOpTest extends TestBase {

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
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("H.<clinit>", "Caught NPE", "H initialized: true");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("H.<clinit>", "Caught NPE", "H initialized: true");
  }

  public static class State {
    public static boolean hInitialized = false;
  }

  public static class H {
    @NeverPropagateValue public static String field = "H.field";

    static {
      State.hInitialized = true;
      System.out.println("H.<clinit>");
    }

    public static void ensureLoaded() {
      // Expected to be inlined and converted to an InitClass instruction.
    }
  }

  public static class TestClass {

    @NeverInline
    public static void process(Object[] dest, int idx, Object v) {
      H.ensureLoaded();
      dest[idx] = v;
      String s = H.field;
      System.out.println(s);
    }

    public static void main(String[] args) {
      Object[] arr = args.length == 42 ? new Object[1] : null;
      try {
        process(arr, 0, "test");
      } catch (NullPointerException e) {
        System.out.println("Caught NPE");
      }
      System.out.println("H initialized: " + State.hInitialized);
    }
  }
}
