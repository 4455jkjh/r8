// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.commonsubexpressionelimination;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CommonSubexpressionEliminationArrayLengthTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addInnerClasses(getClass())
        .release()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("6", "6", "0", "NPE");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("6", "6", "0", "NPE");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject testClass = inspector.clazz(TestClass.class);
    assertEquals(
        1,
        testClass
            .uniqueMethodWithOriginalName("noCatch")
            .streamInstructions()
            .filter(InstructionSubject::isArrayLength)
            .count());
    assertEquals(
        1,
        testClass
            .uniqueMethodWithOriginalName("afterNullCheck")
            .streamInstructions()
            .filter(InstructionSubject::isArrayLength)
            .count());
    assertEquals(
        2,
        testClass
            .uniqueMethodWithOriginalName("differentCatch")
            .streamInstructions()
            .filter(InstructionSubject::isArrayLength)
            .count());
  }

  static class TestClass {

    @NeverInline
    static int noCatch(int[] a) {
      int x = a.length;
      System.out.print("");
      return x + a.length;
    }

    @NeverInline
    static int afterNullCheck(int[] a) {
      if (a == null) {
        return 0;
      }
      int x = a.length;
      System.out.print("");
      return x + a.length;
    }

    @NeverInline
    static int differentCatch(int[] a) {
      try {
        int x = a.length;
        System.out.print("");
        return x;
      } catch (NullPointerException e) {
        return a.length;
      }
    }

    public static void main(String[] args) {
      int[] arr = System.currentTimeMillis() > 0 ? new int[] {1, 2, 3} : null;
      System.out.println(noCatch(arr));
      System.out.println(afterNullCheck(arr));
      System.out.println(afterNullCheck(System.currentTimeMillis() < 0 ? arr : null));
      try {
        System.out.println(differentCatch(System.currentTimeMillis() < 0 ? arr : null));
      } catch (NullPointerException e) {
        System.out.println("NPE");
      }
    }
  }
}
