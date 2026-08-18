// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.controlflow;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.KeepConstantArguments;
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
public class MergeBranchesTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addInnerClasses(MergeBranchesTest.class)
        .release()
        .setMinApi(parameters)
        .compile()
        .inspect(inspector -> inspectMerged(inspector, 1))
        .inspect(this::inspectDifferentSsa)
        .inspect(this::inspectNoTargetJoinBlock)
        .inspect(this::inspectMaterializePhi)
        .inspect(this::inspectTriangle)
        .inspect(this::inspectTriangleInverted)
        .inspect(this::inspectSwappedOperandsEquals)
        .inspect(this::inspectSwappedOperandsEqualsInverted)
        .inspect(this::inspectSwappedOperandsRelational)
        .inspect(this::inspectSwappedOperandsRelationalInverted)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(
            "36", "16", "36", "16", "13", "10", "6", "10", "4", "4", "10", "10", "4", "6", "8",
            "10", "4", "6", "8");
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeDexRuntime();
    testForR8(parameters.getBackend())
        .addInnerClasses(MergeBranchesTest.class)
        .addKeepMainRule(TestClass.class)
        .enableConstantArgumentAnnotations()
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(inspector -> inspectMerged(inspector, 1))
        .inspect(this::inspectDifferentSsa)
        .inspect(this::inspectNoTargetJoinBlock)
        .inspect(this::inspectMaterializePhi)
        .inspect(this::inspectTriangle)
        .inspect(this::inspectTriangleInverted)
        .inspect(this::inspectSwappedOperandsEquals)
        .inspect(this::inspectSwappedOperandsEqualsInverted)
        .inspect(this::inspectSwappedOperandsRelational)
        .inspect(this::inspectSwappedOperandsRelationalInverted)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(
            "36", "16", "36", "16", "13", "10", "6", "10", "4", "4", "10", "10", "4", "6", "8",
            "10", "4", "6", "8");
  }

  private void inspectMerged(CodeInspector inspector, int expectedIfCount) {
    MethodSubject testMethod =
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("testIdenticalSsa");
    long ifCount = testMethod.streamInstructions().filter(InstructionSubject::isIf).count();
    assertEquals(expectedIfCount, ifCount);
  }

  private void inspectDifferentSsa(CodeInspector inspector) {
    MethodSubject testMethod =
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("testDifferentSsa");
    long ifCount = testMethod.streamInstructions().filter(InstructionSubject::isIf).count();
    assertEquals(4, ifCount);
  }

  private void inspectNoTargetJoinBlock(CodeInspector inspector) {
    MethodSubject testMethod =
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("testNoTargetJoinBlock");
    long ifCount = testMethod.streamInstructions().filter(InstructionSubject::isIf).count();
    assertEquals(2, ifCount);
  }

  private void inspectMaterializePhi(CodeInspector inspector) {
    MethodSubject testMethod =
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("testMaterializePhi");
    long ifCount = testMethod.streamInstructions().filter(InstructionSubject::isIf).count();
    assertEquals(1, ifCount);
  }

  private void inspectTriangle(CodeInspector inspector) {
    MethodSubject testMethod =
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("testTriangle");
    long ifCount = testMethod.streamInstructions().filter(InstructionSubject::isIf).count();
    assertEquals(1, ifCount);
  }

  private void inspectTriangleInverted(CodeInspector inspector) {
    MethodSubject testMethod =
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("testTriangleInverted");
    long ifCount = testMethod.streamInstructions().filter(InstructionSubject::isIf).count();
    assertEquals(1, ifCount);
  }

  private void inspectSwappedOperandsEquals(CodeInspector inspector) {
    MethodSubject testMethod =
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("testSwappedOperandsEquals");
    long ifCount = testMethod.streamInstructions().filter(InstructionSubject::isIf).count();
    assertEquals(1, ifCount);
  }

  private void inspectSwappedOperandsEqualsInverted(CodeInspector inspector) {
    MethodSubject testMethod =
        inspector
            .clazz(TestClass.class)
            .uniqueMethodWithOriginalName("testSwappedOperandsEqualsInverted");
    long ifCount = testMethod.streamInstructions().filter(InstructionSubject::isIf).count();
    assertEquals(1, ifCount);
  }

  private void inspectSwappedOperandsRelational(CodeInspector inspector) {
    MethodSubject testMethod =
        inspector
            .clazz(TestClass.class)
            .uniqueMethodWithOriginalName("testSwappedOperandsRelational");
    long ifCount = testMethod.streamInstructions().filter(InstructionSubject::isIf).count();
    assertEquals(1, ifCount);
  }

  private void inspectSwappedOperandsRelationalInverted(CodeInspector inspector) {
    MethodSubject testMethod =
        inspector
            .clazz(TestClass.class)
            .uniqueMethodWithOriginalName("testSwappedOperandsRelationalInverted");
    long ifCount = testMethod.streamInstructions().filter(InstructionSubject::isIf).count();
    assertEquals(1, ifCount);
  }

  static class TestClass {

    @KeepConstantArguments
    @NeverInline
    public static int testIdenticalSsa(
        boolean cond,
        int left,
        int layoutLeft,
        int top,
        int layoutTop,
        int right,
        int layoutRight,
        int bottom,
        int layoutBottom) {
      final int sweepLeft = cond ? (left + layoutLeft) : left;
      final int sweepTop = cond ? (top + layoutTop) : top;
      final int sweepRight = cond ? (right + layoutRight) : right;
      final int sweepBottom = cond ? (bottom + layoutBottom) : bottom;
      return sweepLeft + sweepTop + sweepRight + sweepBottom;
    }

    @KeepConstantArguments
    @NeverInline
    public static int testDifferentSsa(
        boolean[] conds,
        int left,
        int layoutLeft,
        int top,
        int layoutTop,
        int right,
        int layoutRight,
        int bottom,
        int layoutBottom) {
      final int sweepLeft = conds[0] ? (left + layoutLeft) : left;
      final int sweepTop = conds[1] ? (top + layoutTop) : top;
      final int sweepRight = conds[2] ? (right + layoutRight) : right;
      final int sweepBottom = conds[3] ? (bottom + layoutBottom) : bottom;
      return sweepLeft + sweepTop + sweepRight + sweepBottom;
    }

    @KeepConstantArguments
    @NeverInline
    public static int testNoTargetJoinBlock(boolean cond, int left, int layoutLeft) {
      final int sweepLeft = cond ? (left + layoutLeft) : left;
      if (cond) {
        return sweepLeft + 10;
      } else {
        throw new RuntimeException();
      }
    }

    @KeepConstantArguments
    @NeverInline
    public static int testMaterializePhi(
        boolean cond, int left, int layoutLeft, int top, int layoutTop) {
      int sweepLeft = cond ? (left + layoutLeft) : (left + 1);
      int sweepTop = cond ? (top + layoutTop) : (top + 1);
      return sweepLeft + sweepTop;
    }

    @KeepConstantArguments
    @NeverInline
    public static int testTriangle(boolean cond, int left, int layoutLeft, int top, int layoutTop) {
      int sweepLeft = left;
      if (cond) {
        sweepLeft += layoutLeft;
      }
      int sweepTop = top;
      if (cond) {
        sweepTop += layoutTop;
      }
      return sweepLeft + sweepTop;
    }

    @KeepConstantArguments
    @NeverInline
    public static int testTriangleInverted(
        boolean cond, int left, int layoutLeft, int top, int layoutTop) {
      int sweepLeft = left;
      if (!cond) {
        sweepLeft += layoutLeft;
      }
      int sweepTop = top;
      if (!cond) {
        sweepTop += layoutTop;
      }
      return sweepLeft + sweepTop;
    }

    @KeepConstantArguments
    @NeverInline
    public static int testSwappedOperandsEquals(
        int a, int b, int left, int layoutLeft, int top, int layoutTop) {
      final int sweepLeft = (a == b) ? (left + layoutLeft) : left;
      final int sweepTop = (b == a) ? (top + layoutTop) : top;
      return sweepLeft + sweepTop;
    }

    @KeepConstantArguments
    @NeverInline
    public static int testSwappedOperandsEqualsInverted(
        int a, int b, int left, int layoutLeft, int top, int layoutTop) {
      final int sweepLeft = (a == b) ? (left + layoutLeft) : left;
      final int sweepTop = (b != a) ? (top + layoutTop) : top;
      return sweepLeft + sweepTop;
    }

    @KeepConstantArguments
    @NeverInline
    public static int testSwappedOperandsRelational(
        int a, int b, int left, int layoutLeft, int top, int layoutTop) {
      final int sweepLeft = (a < b) ? (left + layoutLeft) : left;
      final int sweepTop = (b > a) ? (top + layoutTop) : top;
      return sweepLeft + sweepTop;
    }

    @KeepConstantArguments
    @NeverInline
    public static int testSwappedOperandsRelationalInverted(
        int a, int b, int left, int layoutLeft, int top, int layoutTop) {
      final int sweepLeft = (a < b) ? (left + layoutLeft) : left;
      final int sweepTop = (b <= a) ? (top + layoutTop) : top;
      return sweepLeft + sweepTop;
    }

    public static void main(String[] args) {
      boolean[] condsTrue = new boolean[] {true, true, true, true};
      boolean[] condsFalse = new boolean[] {false, false, false, false};
      System.out.println(testIdenticalSsa(true, 1, 2, 3, 4, 5, 6, 7, 8));
      System.out.println(testIdenticalSsa(false, 1, 2, 3, 4, 5, 6, 7, 8));
      System.out.println(testDifferentSsa(condsTrue, 1, 2, 3, 4, 5, 6, 7, 8));
      System.out.println(testDifferentSsa(condsFalse, 1, 2, 3, 4, 5, 6, 7, 8));
      System.out.println(testNoTargetJoinBlock(true, 1, 2));
      System.out.println(testMaterializePhi(true, 1, 2, 3, 4));
      System.out.println(testMaterializePhi(false, 1, 2, 3, 4));
      System.out.println(testTriangle(true, 1, 2, 3, 4));
      System.out.println(testTriangle(false, 1, 2, 3, 4));
      System.out.println(testTriangleInverted(true, 1, 2, 3, 4));
      System.out.println(testTriangleInverted(false, 1, 2, 3, 4));
      System.out.println(testSwappedOperandsEquals(1, 1, 1, 2, 3, 4));
      System.out.println(testSwappedOperandsEquals(1, 2, 1, 2, 3, 4));
      System.out.println(testSwappedOperandsEqualsInverted(1, 1, 1, 2, 3, 4));
      System.out.println(testSwappedOperandsEqualsInverted(1, 2, 1, 2, 3, 4));
      System.out.println(testSwappedOperandsRelational(1, 2, 1, 2, 3, 4));
      System.out.println(testSwappedOperandsRelational(2, 1, 1, 2, 3, 4));
      System.out.println(testSwappedOperandsRelationalInverted(1, 2, 1, 2, 3, 4));
      System.out.println(testSwappedOperandsRelationalInverted(2, 1, 1, 2, 3, 4));
    }
  }
}
