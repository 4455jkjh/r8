// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.library.primitive;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithHolder;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DoubleMethodOptimizerTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "42", // byteValue
          "-1", // compare
          "1", // compare
          "0", // compare
          "-1", // compareTo
          "4631107791820423168", // doubleToLongBits
          "4631107791820423168", // doubleToRawLongBits
          "42.0", // doubleValue
          "true", // equals
          "false", // equals
          "42.0", // floatValue
          "1078263808", // hashCode
          "1078263808", // static hashCode
          "42", // intValue
          "true", // isFinite
          "false", // isInfinite
          "false", // staticIsInfinite
          "false", // isNaN
          "false", // staticIsNaN
          "42.0", // longBitsToDouble
          "42", // longValue
          "20.0", // max
          "10.0", // min
          "42.0", // parseDouble
          "42", // shortValue
          "30.0", // sum
          "0x1.5p5", // toHexString
          "42.0", // toString
          "42.0", // static toString
          "42.0", // valueOf(double)
          "42.0"); // valueOf(String)

  @Test
  public void testD8Release() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addInnerClasses(getClass())
        .release()
        .compile()
        .inspect(inspector -> inspect(inspector, false))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .compile()
        .inspect(inspector -> inspect(inspector, true))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private void inspect(CodeInspector inspector, boolean isR8) {
    ClassSubject mainClass = inspector.clazz(Main.class);
    if (isR8) {
      verifyMethodHasNoDoubleInvokes(mainClass.uniqueMethodWithOriginalName("testByteValue"));
      verifyMethodHasNoDoubleInvokes(mainClass.uniqueMethodWithOriginalName("testDoubleValue"));
      verifyMethodHasNoDoubleInvokes(mainClass.uniqueMethodWithOriginalName("testIntValue"));
      verifyMethodHasNoDoubleInvokes(mainClass.uniqueMethodWithOriginalName("testShortValue"));
    }
    boolean isAtLeastK =
        parameters.isCfRuntime()
            || parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.K);
    boolean isAtLeastN =
        parameters.isCfRuntime()
            || parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N);

    if (isAtLeastK) {
      verifyMethodHasNoDoubleInvokes(mainClass.uniqueMethodWithOriginalName("testCompare"));
    }
    if (isAtLeastN) {
      verifyMethodHasNoDoubleInvokes(mainClass.uniqueMethodWithOriginalName("testStaticHashCode"));
      verifyMethodHasNoDoubleInvokes(mainClass.uniqueMethodWithOriginalName("testIsFinite"));
      verifyMethodHasNoDoubleInvokes(mainClass.uniqueMethodWithOriginalName("testMax"));
      verifyMethodHasNoDoubleInvokes(mainClass.uniqueMethodWithOriginalName("testMin"));
      verifyMethodHasNoDoubleInvokes(mainClass.uniqueMethodWithOriginalName("testSum"));
    }

    verifyMethodHasNoDoubleInvokes(mainClass.uniqueMethodWithOriginalName("testCompareTo"));
    verifyMethodHasNoDoubleInvokes(mainClass.uniqueMethodWithOriginalName("testDoubleToLongBits"));
    verifyMethodHasNoDoubleInvokes(
        mainClass.uniqueMethodWithOriginalName("testDoubleToRawLongBits"));
    verifyMethodHasNoDoubleInvokes(mainClass.uniqueMethodWithOriginalName("testEquals"));
    verifyMethodHasNoDoubleInvokes(mainClass.uniqueMethodWithOriginalName("testFloatValue"));
    verifyMethodHasNoDoubleInvokes(mainClass.uniqueMethodWithOriginalName("testHashCode"));
    verifyMethodHasNoDoubleInvokes(mainClass.uniqueMethodWithOriginalName("testIsInfinite"));
    verifyMethodHasNoDoubleInvokes(mainClass.uniqueMethodWithOriginalName("testStaticIsInfinite"));
    verifyMethodHasNoDoubleInvokes(mainClass.uniqueMethodWithOriginalName("testIsNaN"));
    verifyMethodHasNoDoubleInvokes(mainClass.uniqueMethodWithOriginalName("testStaticIsNaN"));
    verifyMethodHasNoDoubleInvokes(mainClass.uniqueMethodWithOriginalName("testLongBitsToDouble"));
    verifyMethodHasNoDoubleInvokes(mainClass.uniqueMethodWithOriginalName("testLongValue"));
    verifyMethodHasNoDoubleInvokes(mainClass.uniqueMethodWithOriginalName("testParseDouble"));
    verifyMethodHasNoDoubleInvokes(mainClass.uniqueMethodWithOriginalName("testToHexString"));
    verifyMethodHasNoDoubleInvokes(mainClass.uniqueMethodWithOriginalName("testToString"));
    verifyMethodHasNoDoubleInvokes(mainClass.uniqueMethodWithOriginalName("testStaticToString"));
    verifyMethodHasNoDoubleStringValueOfInvokes(
        mainClass.uniqueMethodWithOriginalName("testValueOfString"));
  }

  private void verifyMethodHasNoDoubleInvokes(MethodSubject method) {
    assertThat(method, not(invokesMethodWithHolder(Double.class)));
  }

  private void verifyMethodHasNoDoubleStringValueOfInvokes(MethodSubject method) {
    assertTrue(
        method
            .streamInstructions()
            .noneMatch(
                i ->
                    i.isInvokeStatic()
                        && i.getMethod().getHolderType().getName().equals("java.lang.Double")
                        && i.getMethod().getName().equals("valueOf")
                        && i.getMethod().getParameter(0).getName().equals("java.lang.String")));
  }

  static class Main {

    @NeverInline
    static void testByteValue() {
      System.out.println(Double.valueOf(42.0).byteValue());
    }

    @NeverInline
    static void testCompare() {
      System.out.println(Double.compare(10.0, 20.0));
      System.out.println(Double.compare(20.0, 10.0));
      System.out.println(Double.compare(10.0, 10.0));
    }

    @NeverInline
    static void testCompareTo() {
      System.out.println(Double.valueOf(10.0).compareTo(Double.valueOf(20.0)));
    }

    @NeverInline
    static void testDoubleToLongBits() {
      System.out.println(Double.doubleToLongBits(42.0));
    }

    @NeverInline
    static void testDoubleToRawLongBits() {
      System.out.println(Double.doubleToRawLongBits(42.0));
    }

    @NeverInline
    static void testDoubleValue() {
      System.out.println(Double.valueOf(42.0).doubleValue());
    }

    @NeverInline
    static void testEquals() {
      System.out.println(Double.valueOf(42.0).equals(Double.valueOf(42.0)));
      System.out.println(Double.valueOf(42.0).equals(Double.valueOf(43.0)));
    }

    @NeverInline
    static void testFloatValue() {
      System.out.println(Double.valueOf(42.0).floatValue());
    }

    @NeverInline
    static void testHashCode() {
      System.out.println(Double.valueOf(42.0).hashCode());
    }

    @NeverInline
    static void testStaticHashCode() {
      System.out.println(Double.hashCode(42.0));
    }

    @NeverInline
    static void testIntValue() {
      System.out.println(Double.valueOf(42.0).intValue());
    }

    @NeverInline
    static void testIsFinite() {
      System.out.println(Double.isFinite(42.0));
    }

    @NeverInline
    static void testIsInfinite() {
      System.out.println(Double.valueOf(42.0).isInfinite());
    }

    @NeverInline
    static void testStaticIsInfinite() {
      System.out.println(Double.isInfinite(42.0));
    }

    @NeverInline
    static void testIsNaN() {
      System.out.println(Double.valueOf(42.0).isNaN());
    }

    @NeverInline
    static void testStaticIsNaN() {
      System.out.println(Double.isNaN(42.0));
    }

    @NeverInline
    static void testLongBitsToDouble() {
      System.out.println(Double.longBitsToDouble(4631107791820423168L));
    }

    @NeverInline
    static void testLongValue() {
      System.out.println(Double.valueOf(42.0).longValue());
    }

    @NeverInline
    static void testMax() {
      System.out.println(Double.max(10.0, 20.0));
    }

    @NeverInline
    static void testMin() {
      System.out.println(Double.min(10.0, 20.0));
    }

    @NeverInline
    static void testParseDouble() {
      System.out.println(Double.parseDouble("42.0"));
    }

    @NeverInline
    static void testShortValue() {
      System.out.println(Double.valueOf(42.0).shortValue());
    }

    @NeverInline
    static void testSum() {
      System.out.println(Double.sum(10.0, 20.0));
    }

    @NeverInline
    static void testToHexString() {
      System.out.println(Double.toHexString(42.0));
    }

    @NeverInline
    static void testToString() {
      System.out.println(Double.valueOf(42.0).toString());
    }

    @NeverInline
    static void testStaticToString() {
      System.out.println(Double.toString(42.0));
    }

    @NeverInline
    static void testValueOfDouble() {
      System.out.println(Double.valueOf(42.0));
    }

    @NeverInline
    static void testValueOfString() {
      System.out.println(Double.valueOf("42.0"));
    }

    public static void main(String[] args) {
      testByteValue();
      testCompare();
      testCompareTo();
      testDoubleToLongBits();
      testDoubleToRawLongBits();
      testDoubleValue();
      testEquals();
      testFloatValue();
      testHashCode();
      testStaticHashCode();
      testIntValue();
      testIsFinite();
      testIsInfinite();
      testStaticIsInfinite();
      testIsNaN();
      testStaticIsNaN();
      testLongBitsToDouble();
      testLongValue();
      testMax();
      testMin();
      testParseDouble();
      testShortValue();
      testSum();
      testToHexString();
      testToString();
      testStaticToString();
      testValueOfDouble();
      testValueOfString();
    }
  }
}
