// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.ifs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.code.DexCmpLong;
import com.android.tools.r8.dex.code.DexCmpgDouble;
import com.android.tools.r8.dex.code.DexCmpgFloat;
import com.android.tools.r8.dex.code.DexCmplDouble;
import com.android.tools.r8.dex.code.DexCmplFloat;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.internal.exceptions.Unreachable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KnownBooleanShiftDiamondTest extends TestBase {

  private static final String[] EXPECTED =
      new String[] {
        "4",
        "2",
        "4",
        "2",
        "32",
        "16",
        "2",
        "4",
        "2",
        "4",
        "16",
        "32",
        "-2",
        "-4",
        "1073741824",
        "-2147483648",
        "0",
        "1",
        "1",
        "0",
        "2",
        "4",
        // cmpLongGezTo0Or1
        "0",
        "1",
        "0",
        // cmpLongGezTo1Or0
        "1",
        "0",
        "1",
        // cmpLongLtzTo0Or1
        "1",
        "0",
        "1",
        // cmpLongLtzTo1Or0
        "0",
        "1",
        "0",
        // cmpFloatGezTo0Or1
        "0",
        "1",
        "0",
        "1",
        "1",
        "1",
        // cmpFloatGezTo1Or0
        "1",
        "0",
        "1",
        "0",
        "0",
        "0",
        // cmpFloatLtzTo0Or1
        "1",
        "0",
        "1",
        "1",
        "1",
        "1",
        // cmpFloatLtzTo1Or0
        "0",
        "1",
        "0",
        "0",
        "0",
        "0",
        // cmpDoubleGezTo0Or1
        "0",
        "1",
        "0",
        "1",
        "1",
        "1",
        // cmpDoubleGezTo1Or0
        "1",
        "0",
        "1",
        "0",
        "0",
        "0",
        // cmpDoubleLtzTo0Or1
        "1",
        "0",
        "1",
        "1",
        "1",
        "1",
        // cmpDoubleLtzTo1Or0
        "0",
        "1",
        "0",
        "0",
        "0",
        "0"
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
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addInnerClasses(getClass())
        .release()
        .compile()
        .inspect(this::inspect)
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
    int checkedMethods = 0;
    for (FoundMethodSubject method :
        inspector.clazz(Main.class).allMethods(m -> !m.isInstanceInitializer())) {
      String methodName = method.getOriginalMethodName();
      if (methodName.equals("main")) {
        continue;
      }
      checkedMethods++;
      if (parameters.canHaveDalvikIntUsedAsNonIntPrimitiveTypeBug()
          || methodName.startsWith("cmp")) {
        assertTrue(methodName, method.streamInstructions().anyMatch(InstructionSubject::isIf));
        assertTrue(
            methodName,
            method.streamInstructions().noneMatch(InstructionSubject::isIntLogicalBinop));
      } else {
        assertTrue(methodName, method.streamInstructions().noneMatch(InstructionSubject::isIf));
        assertTrue(
            methodName,
            method.streamInstructions().anyMatch(InstructionSubject::isIntLogicalBinop));
      }
      if (parameters.isDexRuntime() && methodName.startsWith("cmp")) {
        Class<? extends DexInstruction> expectedCmp = getExpectedDexCmp(methodName);
        assertTrue(
            methodName,
            method
                .streamInstructions()
                .anyMatch(i -> expectedCmp.isInstance(i.asDexInstruction().getInstruction())));
      }
    }
    assertEquals(23, checkedMethods);
  }

  private static Class<? extends DexInstruction> getExpectedDexCmp(String methodName) {
    if (methodName.startsWith("cmpLong")) {
      return DexCmpLong.class;
    } else if (methodName.startsWith("cmpFloatGez")) {
      return DexCmplFloat.class;
    } else if (methodName.startsWith("cmpFloatLtz")) {
      return DexCmpgFloat.class;
    } else if (methodName.startsWith("cmpDoubleGez")) {
      return DexCmplDouble.class;
    } else if (methodName.startsWith("cmpDoubleLtz")) {
      return DexCmpgDouble.class;
    }
    throw new Unreachable(methodName);
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.println(shl(true));
      System.out.println(shl(false));

      System.out.println(shlInverted(true));
      System.out.println(shlInverted(false));

      System.out.println(shl16To32(true));
      System.out.println(shl16To32(false));

      System.out.println(ushr(true));
      System.out.println(ushr(false));

      System.out.println(ushrInverted(true));
      System.out.println(ushrInverted(false));

      System.out.println(ushr32To16(true));
      System.out.println(ushr32To16(false));

      System.out.println(shrNegative(true));
      System.out.println(shrNegative(false));

      System.out.println(ushrMinValue(true));
      System.out.println(ushrMinValue(false));

      System.out.println(gezTo0Or1(5));
      System.out.println(gezTo0Or1(-5));

      System.out.println(gezTo1Or0(5));
      System.out.println(gezTo1Or0(-5));

      System.out.println(gezChainedWith2Or4(5));
      System.out.println(gezChainedWith2Or4(-5));

      System.out.println(cmpLongGezTo0Or1(5L, 3L));
      System.out.println(cmpLongGezTo0Or1(3L, 5L));
      System.out.println(cmpLongGezTo0Or1(3L, 3L));

      System.out.println(cmpLongGezTo1Or0(5L, 3L));
      System.out.println(cmpLongGezTo1Or0(3L, 5L));
      System.out.println(cmpLongGezTo1Or0(3L, 3L));

      System.out.println(cmpLongLtzTo0Or1(5L, 3L));
      System.out.println(cmpLongLtzTo0Or1(3L, 5L));
      System.out.println(cmpLongLtzTo0Or1(3L, 3L));

      System.out.println(cmpLongLtzTo1Or0(5L, 3L));
      System.out.println(cmpLongLtzTo1Or0(3L, 5L));
      System.out.println(cmpLongLtzTo1Or0(3L, 3L));

      System.out.println(cmpFloatGezTo0Or1(5.0f, 3.0f));
      System.out.println(cmpFloatGezTo0Or1(3.0f, 5.0f));
      System.out.println(cmpFloatGezTo0Or1(3.0f, 3.0f));
      System.out.println(cmpFloatGezTo0Or1(Float.NaN, 3.0f));
      System.out.println(cmpFloatGezTo0Or1(3.0f, Float.NaN));
      System.out.println(cmpFloatGezTo0Or1(Float.NaN, Float.NaN));

      System.out.println(cmpFloatGezTo1Or0(5.0f, 3.0f));
      System.out.println(cmpFloatGezTo1Or0(3.0f, 5.0f));
      System.out.println(cmpFloatGezTo1Or0(3.0f, 3.0f));
      System.out.println(cmpFloatGezTo1Or0(Float.NaN, 3.0f));
      System.out.println(cmpFloatGezTo1Or0(3.0f, Float.NaN));
      System.out.println(cmpFloatGezTo1Or0(Float.NaN, Float.NaN));

      System.out.println(cmpFloatLtzTo0Or1(5.0f, 3.0f));
      System.out.println(cmpFloatLtzTo0Or1(3.0f, 5.0f));
      System.out.println(cmpFloatLtzTo0Or1(3.0f, 3.0f));
      System.out.println(cmpFloatLtzTo0Or1(Float.NaN, 3.0f));
      System.out.println(cmpFloatLtzTo0Or1(3.0f, Float.NaN));
      System.out.println(cmpFloatLtzTo0Or1(Float.NaN, Float.NaN));

      System.out.println(cmpFloatLtzTo1Or0(5.0f, 3.0f));
      System.out.println(cmpFloatLtzTo1Or0(3.0f, 5.0f));
      System.out.println(cmpFloatLtzTo1Or0(3.0f, 3.0f));
      System.out.println(cmpFloatLtzTo1Or0(Float.NaN, 3.0f));
      System.out.println(cmpFloatLtzTo1Or0(3.0f, Float.NaN));
      System.out.println(cmpFloatLtzTo1Or0(Float.NaN, Float.NaN));

      System.out.println(cmpDoubleGezTo0Or1(5.0, 3.0));
      System.out.println(cmpDoubleGezTo0Or1(3.0, 5.0));
      System.out.println(cmpDoubleGezTo0Or1(3.0, 3.0));
      System.out.println(cmpDoubleGezTo0Or1(Double.NaN, 3.0));
      System.out.println(cmpDoubleGezTo0Or1(3.0, Double.NaN));
      System.out.println(cmpDoubleGezTo0Or1(Double.NaN, Double.NaN));

      System.out.println(cmpDoubleGezTo1Or0(5.0, 3.0));
      System.out.println(cmpDoubleGezTo1Or0(3.0, 5.0));
      System.out.println(cmpDoubleGezTo1Or0(3.0, 3.0));
      System.out.println(cmpDoubleGezTo1Or0(Double.NaN, 3.0));
      System.out.println(cmpDoubleGezTo1Or0(3.0, Double.NaN));
      System.out.println(cmpDoubleGezTo1Or0(Double.NaN, Double.NaN));

      System.out.println(cmpDoubleLtzTo0Or1(5.0, 3.0));
      System.out.println(cmpDoubleLtzTo0Or1(3.0, 5.0));
      System.out.println(cmpDoubleLtzTo0Or1(3.0, 3.0));
      System.out.println(cmpDoubleLtzTo0Or1(Double.NaN, 3.0));
      System.out.println(cmpDoubleLtzTo0Or1(3.0, Double.NaN));
      System.out.println(cmpDoubleLtzTo0Or1(Double.NaN, Double.NaN));

      System.out.println(cmpDoubleLtzTo1Or0(5.0, 3.0));
      System.out.println(cmpDoubleLtzTo1Or0(3.0, 5.0));
      System.out.println(cmpDoubleLtzTo1Or0(3.0, 3.0));
      System.out.println(cmpDoubleLtzTo1Or0(Double.NaN, 3.0));
      System.out.println(cmpDoubleLtzTo1Or0(3.0, Double.NaN));
      System.out.println(cmpDoubleLtzTo1Or0(Double.NaN, Double.NaN));
    }

    @NeverInline
    public static int shl(boolean b) {
      return b ? 4 : 2;
    }

    @NeverInline
    public static int shlInverted(boolean b) {
      return !b ? 2 : 4;
    }

    @NeverInline
    public static int shl16To32(boolean b) {
      return b ? 32 : 16;
    }

    @NeverInline
    public static int ushr(boolean b) {
      return b ? 2 : 4;
    }

    @NeverInline
    public static int ushrInverted(boolean b) {
      return !b ? 4 : 2;
    }

    @NeverInline
    public static int ushr32To16(boolean b) {
      return b ? 16 : 32;
    }

    @NeverInline
    public static int shrNegative(boolean b) {
      return b ? -2 : -4;
    }

    @NeverInline
    public static int ushrMinValue(boolean b) {
      return b ? 0x40000000 : 0x80000000;
    }

    @NeverInline
    public static int gezTo0Or1(int x) {
      return x >= 0 ? 0 : 1;
    }

    @NeverInline
    public static int gezTo1Or0(int x) {
      return x >= 0 ? 1 : 0;
    }

    @NeverInline
    public static int gezChainedWith2Or4(int x) {
      boolean b = x < 0;
      return b ? 4 : 2;
    }

    @NeverInline
    public static int cmpLongGezTo0Or1(long x, long y) {
      return x >= y ? 0 : 1;
    }

    @NeverInline
    public static int cmpLongGezTo1Or0(long x, long y) {
      return x >= y ? 1 : 0;
    }

    @NeverInline
    public static int cmpLongLtzTo0Or1(long x, long y) {
      return x < y ? 0 : 1;
    }

    @NeverInline
    public static int cmpLongLtzTo1Or0(long x, long y) {
      return x < y ? 1 : 0;
    }

    @NeverInline
    public static int cmpFloatGezTo0Or1(float x, float y) {
      return x >= y ? 0 : 1;
    }

    @NeverInline
    public static int cmpFloatGezTo1Or0(float x, float y) {
      return x >= y ? 1 : 0;
    }

    @NeverInline
    public static int cmpFloatLtzTo0Or1(float x, float y) {
      return x < y ? 0 : 1;
    }

    @NeverInline
    public static int cmpFloatLtzTo1Or0(float x, float y) {
      return x < y ? 1 : 0;
    }

    @NeverInline
    public static int cmpDoubleGezTo0Or1(double x, double y) {
      return x >= y ? 0 : 1;
    }

    @NeverInline
    public static int cmpDoubleGezTo1Or0(double x, double y) {
      return x >= y ? 1 : 0;
    }

    @NeverInline
    public static int cmpDoubleLtzTo0Or1(double x, double y) {
      return x < y ? 0 : 1;
    }

    @NeverInline
    public static int cmpDoubleLtzTo1Or0(double x, double y) {
      return x < y ? 1 : 0;
    }
  }
}
