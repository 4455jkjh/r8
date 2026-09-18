// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.intlongarithmetic;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.internal.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MinMaxArithmeticRewriterTest extends TestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "-100", "42", "-100", "42", "-100", "42", "-100", "42", "-100", "42", "-100", "42",
          "-100", "0", "-100", "0", "5", "42", "5", "42", "-100", "10", "20", "42", "-100", "0",
          "20", "42", "-100", "0", "10", "10", "10", "10", "20", "20", "20", "20", "10", "10", "20",
          "20", "10", "10", "20", "20", "10", "15", "20", "10", "15", "20");

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addProgramClasses(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(Main.class);
    for (FoundMethodSubject method :
        clazz.allMethods(m -> m.getOriginalMethodName().contains("same"))) {
      assertEquals(
          0, method.streamInstructions().filter(InstructionSubject::isInvokeStatic).count());
    }
    for (FoundMethodSubject method :
        clazz.allMethods(m -> m.getOriginalMethodName().contains("successive"))) {
      assertEquals(
          1, method.streamInstructions().filter(InstructionSubject::isInvokeStatic).count());
    }
    for (FoundMethodSubject method :
        clazz.allMethods(m -> m.getOriginalMethodName().contains("clampingFold"))) {
      assertEquals(
          0, method.streamInstructions().filter(InstructionSubject::isInvokeStatic).count());
    }
    for (FoundMethodSubject method :
        clazz.allMethods(m -> m.getOriginalMethodName().contains("clampingNoFold"))) {
      assertEquals(
          2, method.streamInstructions().filter(InstructionSubject::isInvokeStatic).count());
    }
  }

  public static class Main {

    public static void main(String[] args) {
      sameMinInt(-100);
      sameMinInt(42);
      sameMaxInt(-100);
      sameMaxInt(42);
      sameMinLong(-100L);
      sameMinLong(42L);
      sameMaxLong(-100L);
      sameMaxLong(42L);
      sameIntegerMin(-100);
      sameIntegerMin(42);
      sameLongMax(-100L);
      sameLongMax(42L);

      successiveMinIntLeft(-100);
      successiveMinIntLeft(42);
      successiveMinIntRight(-100);
      successiveMinIntRight(42);
      successiveMaxIntLeft(-100);
      successiveMaxIntLeft(42);
      successiveMaxIntRight(-100);
      successiveMaxIntRight(42);
      successiveMinLongLeft(-100L);
      successiveMinLongLeft(42L);
      successiveMaxLongLeft(-100L);
      successiveMaxLongLeft(42L);
      successiveIntegerMin(-100);
      successiveIntegerMin(42);
      successiveLongMax(-100L);
      successiveLongMax(42L);
      successiveTripleMinInt(-100);
      successiveTripleMinInt(42);

      clampingFoldMinMaxInt(-100);
      clampingFoldMinMaxInt(42);
      clampingFoldMinMaxIntRight(-100);
      clampingFoldMinMaxIntRight(42);
      clampingFoldMaxMinInt(-100);
      clampingFoldMaxMinInt(42);
      clampingFoldMaxMinIntRight(-100);
      clampingFoldMaxMinIntRight(42);
      clampingFoldMinMaxLong(-100L);
      clampingFoldMinMaxLong(42L);
      clampingFoldMaxMinLong(-100L);
      clampingFoldMaxMinLong(42L);
      clampingFoldIntegerMinMax(-100);
      clampingFoldIntegerMinMax(42);
      clampingFoldLongMaxMin(-100L);
      clampingFoldLongMaxMin(42L);

      clampingNoFoldMinMaxInt(-100);
      clampingNoFoldMinMaxInt(15);
      clampingNoFoldMinMaxInt(42);
      clampingNoFoldMaxMinInt(-100);
      clampingNoFoldMaxMinInt(15);
      clampingNoFoldMaxMinInt(42);
    }

    @NeverInline
    public static void sameMinInt(int x) {
      System.out.println(Math.min(x, x));
    }

    @NeverInline
    public static void sameMaxInt(int x) {
      System.out.println(Math.max(x, x));
    }

    @NeverInline
    public static void sameMinLong(long x) {
      System.out.println(Math.min(x, x));
    }

    @NeverInline
    public static void sameMaxLong(long x) {
      System.out.println(Math.max(x, x));
    }

    @NeverInline
    public static void sameIntegerMin(int x) {
      System.out.println(Integer.min(x, x));
    }

    @NeverInline
    public static void sameLongMax(long x) {
      System.out.println(Long.max(x, x));
    }

    @NeverInline
    public static void successiveMinIntLeft(int x) {
      System.out.println(Math.min(1, Math.min(0, x)));
    }

    @NeverInline
    public static void successiveMinIntRight(int x) {
      System.out.println(Math.min(Math.min(x, 0), 1));
    }

    @NeverInline
    public static void successiveMaxIntLeft(int x) {
      System.out.println(Math.max(1, Math.max(5, x)));
    }

    @NeverInline
    public static void successiveMaxIntRight(int x) {
      System.out.println(Math.max(Math.max(x, 5), 1));
    }

    @NeverInline
    public static void successiveMinLongLeft(long x) {
      System.out.println(Math.min(10L, Math.min(20L, x)));
    }

    @NeverInline
    public static void successiveMaxLongLeft(long x) {
      System.out.println(Math.max(10L, Math.max(20L, x)));
    }

    @NeverInline
    public static void successiveIntegerMin(int x) {
      System.out.println(Integer.min(1, Integer.min(0, x)));
    }

    @NeverInline
    public static void successiveLongMax(long x) {
      System.out.println(Long.max(10L, Long.max(20L, x)));
    }

    @NeverInline
    public static void successiveTripleMinInt(int x) {
      System.out.println(Math.min(2, Math.min(1, Math.min(0, x))));
    }

    @NeverInline
    public static void clampingFoldMinMaxInt(int x) {
      System.out.println(Math.min(10, Math.max(20, x)));
    }

    @NeverInline
    public static void clampingFoldMinMaxIntRight(int x) {
      System.out.println(Math.min(Math.max(x, 20), 10));
    }

    @NeverInline
    public static void clampingFoldMaxMinInt(int x) {
      System.out.println(Math.max(20, Math.min(10, x)));
    }

    @NeverInline
    public static void clampingFoldMaxMinIntRight(int x) {
      System.out.println(Math.max(Math.min(x, 10), 20));
    }

    @NeverInline
    public static void clampingFoldMinMaxLong(long x) {
      System.out.println(Math.min(10L, Math.max(20L, x)));
    }

    @NeverInline
    public static void clampingFoldMaxMinLong(long x) {
      System.out.println(Math.max(20L, Math.min(10L, x)));
    }

    @NeverInline
    public static void clampingFoldIntegerMinMax(int x) {
      System.out.println(Integer.min(10, Integer.max(20, x)));
    }

    @NeverInline
    public static void clampingFoldLongMaxMin(long x) {
      System.out.println(Long.max(20L, Long.min(10L, x)));
    }

    @NeverInline
    public static void clampingNoFoldMinMaxInt(int x) {
      System.out.println(Math.min(20, Math.max(10, x)));
    }

    @NeverInline
    public static void clampingNoFoldMaxMinInt(int x) {
      System.out.println(Math.max(10, Math.min(20, x)));
    }
  }
}
