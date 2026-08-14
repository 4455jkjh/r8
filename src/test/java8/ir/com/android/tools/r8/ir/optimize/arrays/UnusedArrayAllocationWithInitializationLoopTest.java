// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.arrays;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnusedArrayAllocationWithInitializationLoopTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withAllApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .compile()
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    MethodSubject testLeMethod = inspector.clazz(Main.class).uniqueMethodWithOriginalName("testLe");
    assertTrue(testLeMethod.streamInstructions().noneMatch(InstructionSubject::isNewArray));

    MethodSubject testLtMethod = inspector.clazz(Main.class).uniqueMethodWithOriginalName("testLt");
    assertTrue(testLtMethod.streamInstructions().noneMatch(InstructionSubject::isNewArray));

    MethodSubject testNeMethod = inspector.clazz(Main.class).uniqueMethodWithOriginalName("testNe");
    assertTrue(testNeMethod.streamInstructions().noneMatch(InstructionSubject::isNewArray));

    MethodSubject testBackwardsGeMethod =
        inspector.clazz(Main.class).uniqueMethodWithOriginalName("testBackwardsGe");
    assertTrue(
        testBackwardsGeMethod.streamInstructions().noneMatch(InstructionSubject::isNewArray));

    MethodSubject testBackwardsGtMethod =
        inspector.clazz(Main.class).uniqueMethodWithOriginalName("testBackwardsGt");
    assertTrue(
        testBackwardsGtMethod.streamInstructions().noneMatch(InstructionSubject::isNewArray));

    MethodSubject testBackwardsNezMethod =
        inspector.clazz(Main.class).uniqueMethodWithOriginalName("testBackwardsNez");
    assertTrue(
        testBackwardsNezMethod.streamInstructions().noneMatch(InstructionSubject::isNewArray));

    MethodSubject testOneIterationLeMethod =
        inspector.clazz(Main.class).uniqueMethodWithOriginalName("testOneIterationLe");
    assertTrue(
        testOneIterationLeMethod.streamInstructions().noneMatch(InstructionSubject::isNewArray));

    MethodSubject testOneIterationLtMethod =
        inspector.clazz(Main.class).uniqueMethodWithOriginalName("testOneIterationLt");
    assertTrue(
        testOneIterationLtMethod.streamInstructions().noneMatch(InstructionSubject::isNewArray));
  }

  static class Main {

    public static void main(String[] args) {
      testLe();
      testLt();
      testNe();
      testBackwardsGe();
      testBackwardsGt();
      testBackwardsNez();
      testOneIterationLe();
      testOneIterationLt();
    }

    @NeverInline
    private static void testLe() {
      int[] unusedArray = new int[3];
      for (int i = 0; i <= unusedArray.length - 1; i++) {
        unusedArray[i] = i;
      }
      System.out.print("");
    }

    @NeverInline
    private static void testLt() {
      int[] unusedArray = new int[3];
      for (int i = 0; i < unusedArray.length; i++) {
        unusedArray[i] = i;
      }
      System.out.print("");
    }

    @NeverInline
    private static void testNe() {
      int[] unusedArray = new int[3];
      for (int i = 0; i != unusedArray.length; i++) {
        unusedArray[i] = i;
      }
      System.out.print("");
    }

    @NeverInline
    private static void testBackwardsGe() {
      int[] unusedArray = new int[3];
      for (int i = unusedArray.length - 1; i >= 0; i--) {
        unusedArray[i] = i;
      }
      System.out.print("");
    }

    @NeverInline
    private static void testBackwardsGt() {
      int[] unusedArray = new int[3];
      for (int i = unusedArray.length - 1; i > 0; i--) {
        unusedArray[i] = i;
      }
      System.out.print("");
    }

    @NeverInline
    private static void testBackwardsNez() {
      int[] unusedArray = new int[3];
      for (int i = unusedArray.length - 1; i != 0; i--) {
        unusedArray[i] = i;
      }
      System.out.print("");
    }

    @NeverInline
    private static void testOneIterationLe() {
      int[] unusedArray = new int[1];
      for (int i = 0; i <= unusedArray.length - 1; i++) {
        unusedArray[i] = i;
      }
      System.out.print("");
    }

    @NeverInline
    private static void testOneIterationLt() {
      int[] unusedArray = new int[1];
      for (int i = 0; i < unusedArray.length; i++) {
        unusedArray[i] = i;
      }
      System.out.print("");
    }
  }
}
