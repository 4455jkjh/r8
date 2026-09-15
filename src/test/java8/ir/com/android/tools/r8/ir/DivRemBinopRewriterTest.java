// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir;

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
public class DivRemBinopRewriterTest extends TestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "-42",
          "-2147483648",
          "0",
          "-42",
          "-9223372036854775808",
          "0",
          "-84",
          "4294967296",
          "0",
          "-84",
          "0");

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
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
        clazz.allMethods(m -> m.getOriginalMethodName().contains("rem"))) {
      assertEquals(
          0,
          method
              .streamInstructions()
              .filter(i -> i.isIntArithmeticBinop() || i.isLongArithmeticBinop())
              .count());
    }
    for (FoundMethodSubject method :
        clazz.allMethods(m -> m.getOriginalMethodName().contains("divide"))) {
      assertEquals(0, method.streamInstructions().filter(InstructionSubject::isDivision).count());
    }
    for (FoundMethodSubject method :
        clazz.allMethods(m -> m.getOriginalMethodName().contains("successive"))) {
      assertEquals(
          1, method.streamInstructions().filter(InstructionSubject::isMultiplication).count());
    }
  }

  public static class Main {

    public static void main(String[] args) {
      remInt1(0);
      remInt1(42);
      remInt1(Integer.MIN_VALUE);
      remLong1(0);
      remLong1(42);
      remLong1(Long.MIN_VALUE);

      remIntM1(0);
      remIntM1(42);
      remIntM1(Integer.MIN_VALUE);
      remLongM1(0);
      remLongM1(42);
      remLongM1(Long.MIN_VALUE);

      divideInt(0);
      divideInt(42);
      divideInt(Integer.MIN_VALUE);
      divideLong(0);
      divideLong(42);
      divideLong(Long.MIN_VALUE);

      successiveInt(0);
      successiveInt(42);
      successiveInt(Integer.MIN_VALUE);
      successiveLong(0);
      successiveLong(42);
      successiveLong(Long.MIN_VALUE);
    }

    @NeverInline
    public static void remInt1(int x) {
      System.out.println(x % 1);
    }

    @NeverInline
    public static void remLong1(long x) {
      System.out.println(x % 1);
    }

    @NeverInline
    public static void remIntM1(int x) {
      System.out.println(x % -1);
    }

    @NeverInline
    public static void remLongM1(long x) {
      System.out.println(x % -1);
    }

    @NeverInline
    public static void divideInt(int x) {
      System.out.println(x / -1);
    }

    @NeverInline
    public static void divideLong(long x) {
      System.out.println(x / -1);
    }

    @NeverInline
    public static void successiveInt(long x) {
      System.out.println((x / -1) * 2);
    }

    @NeverInline
    public static void successiveLong(long x) {
      System.out.println((x / -1) * 2);
    }
  }
}
