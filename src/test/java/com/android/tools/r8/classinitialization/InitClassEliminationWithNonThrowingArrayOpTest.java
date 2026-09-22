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
public class InitClassEliminationWithNonThrowingArrayOpTest extends TestBase {

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
        .assertSuccessWithOutputLines(
            "In clinit: 0", "H1.field", "Read from clinit: 99", "H2.field");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(
            "In clinit: 0", "H1.field", "Read from clinit: 99", "H2.field");
  }

  public static class State {
    public static int[][] arrayHolder = new int[1][];
  }

  public static class H1 {
    @NeverPropagateValue public static String field = "H1.field";

    static {
      // Reads arrayHolder[0][0] before array write
      int val = State.arrayHolder[0][0];
      System.out.println("In clinit: " + val);
    }

    public static void ensureLoaded() {
      // Expected to be inlined and converted to an InitClass instruction.
    }
  }

  public static class H2 {
    @NeverPropagateValue public static String field = "H2.field";

    static {
      // Writes arrayHolder[0][0] before array read
      State.arrayHolder[0][0] = 99;
    }

    public static void ensureLoaded() {
      // Expected to be inlined and converted to an InitClass instruction.
    }
  }

  public static class TestClass {

    @NeverInline
    public static void testArrayPut() {
      int[] arr = new int[2];
      State.arrayHolder[0] = arr;
      H1.ensureLoaded();
      arr[0] = 42; // Non-throwing ArrayPut
      String s = H1.field;
      System.out.println(s);
    }

    @NeverInline
    public static void testArrayGet() {
      int[] arr = new int[2];
      State.arrayHolder[0] = arr;
      H2.ensureLoaded();
      int val = arr[0]; // Non-throwing ArrayGet
      String s = H2.field;
      System.out.println("Read from clinit: " + val);
      System.out.println(s);
    }

    public static void main(String[] args) {
      testArrayPut();
      testArrayGet();
    }
  }
}
