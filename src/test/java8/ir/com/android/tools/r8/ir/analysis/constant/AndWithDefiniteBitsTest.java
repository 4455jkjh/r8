// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.constant;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.code.DexAndIntLit8;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AndWithDefiniteBitsTest extends TestBase {

  private static final String[] EXPECTED =
      new String[] {
        "2",
        "4",
        "changed",
        "unchanged",
        "clean",
        "dirty",
        "dirty",
        "dirty",
        "dirty",
        "dirty",
        "clean",
        "dirty",
        "18",
        "18",
        "34",
        "34"
      };

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
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject mainClass = inspector.clazz(Main.class);
    assertThat(mainClass, isPresent());

    // (x & 14) -> x when x in {2, 4}.
    MethodSubject andRedundantMask = mainClass.uniqueMethodWithOriginalName("andRedundantMask");
    assertThat(andRedundantMask, isPresent());
    assertTrue(
        andRedundantMask.streamInstructions().noneMatch(InstructionSubject::isIntLogicalBinop));

    // (x & 11) != 2 -> x != 6 when x in {4, 6}.
    MethodSubject ifAndAllUnknownBitsInMask =
        mainClass.uniqueMethodWithOriginalName("ifAndAllUnknownBitsInMask");
    assertThat(ifAndAllUnknownBitsInMask, isPresent());
    assertTrue(
        ifAndAllUnknownBitsInMask
            .streamInstructions()
            .noneMatch(InstructionSubject::isIntLogicalBinop));
    assertTrue(ifAndAllUnknownBitsInMask.streamInstructions().anyMatch(i -> i.isConstNumber(6)));
    assertTrue(ifAndAllUnknownBitsInMask.streamInstructions().noneMatch(i -> i.isConstNumber(11)));

    // (x & 91) != 18 -> (x & 17) != 16 when x in {18, 19, 34, 35}.
    MethodSubject ifAndPartialKnownBitsInMask =
        mainClass.uniqueMethodWithOriginalName("ifAndPartialKnownBitsInMask");
    assertThat(ifAndPartialKnownBitsInMask, isPresent());
    assertTrue(hasAndWithMask(ifAndPartialKnownBitsInMask, 17));
    assertTrue(ifAndPartialKnownBitsInMask.streamInstructions().anyMatch(i -> i.isConstNumber(16)));
    assertTrue(
        ifAndPartialKnownBitsInMask.streamInstructions().noneMatch(i -> i.isConstNumber(18)));

    // (x & 91) != 2 -> (x & 17) != 0 when x in {18, 19, 34, 35}.
    MethodSubject ifAndPartialKnownBitsInMaskZeroComparison =
        mainClass.uniqueMethodWithOriginalName("ifAndPartialKnownBitsInMaskZeroComparison");
    assertThat(ifAndPartialKnownBitsInMaskZeroComparison, isPresent());
    assertTrue(hasAndWithMask(ifAndPartialKnownBitsInMaskZeroComparison, 17));
    assertTrue(
        ifAndPartialKnownBitsInMaskZeroComparison
            .streamInstructions()
            .noneMatch(i -> i.isConstNumber(2)));

    // (x & 126) -> (x & 50) when x in {18, 19, 34, 35}.
    MethodSubject andNarrowMask = mainClass.uniqueMethodWithOriginalName("andNarrowMask");
    assertThat(andNarrowMask, isPresent());
    assertTrue(hasAndWithMask(andNarrowMask, 50));
  }

  private boolean hasAndWithMask(MethodSubject method, int mask) {
    if (parameters.isCfRuntime()) {
      return method.streamInstructions().anyMatch(InstructionSubject::isIntLogicalBinop)
          && method.streamInstructions().anyMatch(i -> i.isConstNumber(mask));
    } else {
      return method
          .streamInstructions()
          .anyMatch(
              i ->
                  i.asDexInstruction().getInstruction() instanceof DexAndIntLit8
                      && ((DexAndIntLit8) i.asDexInstruction().getInstruction()).CC == mask);
    }
  }

  public static class Main {

    public static void main(String[] args) {
      andRedundantMask(2);
      andRedundantMask(4);

      ifAndAllUnknownBitsInMask(4);
      ifAndAllUnknownBitsInMask(6);

      ifAndPartialKnownBitsInMask(18);
      ifAndPartialKnownBitsInMask(19);
      ifAndPartialKnownBitsInMask(34);
      ifAndPartialKnownBitsInMask(35);

      ifAndPartialKnownBitsInMaskZeroComparison(18);
      ifAndPartialKnownBitsInMaskZeroComparison(19);
      ifAndPartialKnownBitsInMaskZeroComparison(34);
      ifAndPartialKnownBitsInMaskZeroComparison(35);

      andNarrowMask(18);
      andNarrowMask(19);
      andNarrowMask(34);
      andNarrowMask(35);
    }

    @NeverInline
    public static void andRedundantMask(int x) {
      System.out.println(x & 14);
    }

    @NeverInline
    public static void ifAndAllUnknownBitsInMask(int x) {
      if ((x & 11) != 2) {
        System.out.println("changed");
      } else {
        System.out.println("unchanged");
      }
    }

    @NeverInline
    public static void ifAndPartialKnownBitsInMask(int x) {
      if ((x & 91) != 18) {
        System.out.println("dirty");
      } else {
        System.out.println("clean");
      }
    }

    @NeverInline
    public static void ifAndPartialKnownBitsInMaskZeroComparison(int x) {
      if ((x & 91) != 2) {
        System.out.println("dirty");
      } else {
        System.out.println("clean");
      }
    }

    @NeverInline
    public static void andNarrowMask(int x) {
      System.out.println(x & 126);
    }
  }
}
