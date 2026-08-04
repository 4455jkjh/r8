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
public class FloatMethodOptimizerTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "3", // byteValue
          "-1", // compare
          "1", // compare
          "0", // compare
          "-1", // compareTo
          "3.5", // doubleValue
          "1065353216", // floatToIntBits
          "1065353216", // floatToRawIntBits
          "3.5", // floatValue
          "1080033280", // hashCode
          "1080033280", // static hashCode
          "1.0", // intBitsToFloat
          "3", // intValue
          "true", // isFinite
          "false", // isFinite
          "false", // isFinite
          "true", // isInfinite
          "false", // isInfinite
          "true", // static isInfinite
          "false", // static isInfinite
          "true", // isNaN
          "false", // isNaN
          "true", // static isNaN
          "false", // static isNaN
          "3", // longValue
          "2.5", // max
          "1.5", // min
          "3.5", // parseFloat
          "3", // shortValue
          "4.0", // sum
          "0x1.0p0", // toHexString
          "3.5", // toString
          "3.5", // static toString
          "3.5", // valueOf(float)
          "3.5"); // valueOf(String)

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
    // Verify constant folding of methods in Main.
    ClassSubject mainClass = inspector.clazz(Main.class);
    verifyMethodHasNoFloatInvokes(mainClass.uniqueMethodWithOriginalName("testByteValue"));
    verifyMethodHasNoFloatInvokes(mainClass.uniqueMethodWithOriginalName("testCompare"));
    verifyMethodHasNoFloatInvokes(mainClass.uniqueMethodWithOriginalName("testCompareTo"));
    verifyMethodHasNoFloatInvokes(mainClass.uniqueMethodWithOriginalName("testDoubleValue"));
    verifyMethodHasNoFloatInvokes(mainClass.uniqueMethodWithOriginalName("testFloatToIntBits"));
    verifyMethodHasNoFloatInvokes(mainClass.uniqueMethodWithOriginalName("testFloatToRawIntBits"));
    if (isR8) {
      verifyMethodHasNoFloatInvokes(mainClass.uniqueMethodWithOriginalName("testFloatValue"));
    }
    verifyMethodHasNoFloatInvokes(mainClass.uniqueMethodWithOriginalName("testHashCode"));
    verifyMethodHasNoFloatInvokes(mainClass.uniqueMethodWithOriginalName("testStaticHashCode"));
    verifyMethodHasNoFloatInvokes(mainClass.uniqueMethodWithOriginalName("testIntBitsToFloat"));
    verifyMethodHasNoFloatInvokes(mainClass.uniqueMethodWithOriginalName("testIntValue"));
    verifyMethodHasNoFloatInvokes(mainClass.uniqueMethodWithOriginalName("testIsFinite"));
    verifyMethodHasNoFloatInvokes(mainClass.uniqueMethodWithOriginalName("testIsInfinite"));
    verifyMethodHasNoFloatInvokes(mainClass.uniqueMethodWithOriginalName("testStaticIsInfinite"));
    verifyMethodHasNoFloatInvokes(mainClass.uniqueMethodWithOriginalName("testIsNaN"));
    verifyMethodHasNoFloatInvokes(mainClass.uniqueMethodWithOriginalName("testStaticIsNaN"));
    verifyMethodHasNoFloatInvokes(mainClass.uniqueMethodWithOriginalName("testLongValue"));
    verifyMethodHasNoFloatInvokes(mainClass.uniqueMethodWithOriginalName("testMax"));
    verifyMethodHasNoFloatInvokes(mainClass.uniqueMethodWithOriginalName("testMin"));
    verifyMethodHasNoFloatInvokes(mainClass.uniqueMethodWithOriginalName("testParseFloat"));
    verifyMethodHasNoFloatInvokes(mainClass.uniqueMethodWithOriginalName("testShortValue"));
    verifyMethodHasNoFloatInvokes(mainClass.uniqueMethodWithOriginalName("testSum"));
    verifyMethodHasNoFloatInvokes(mainClass.uniqueMethodWithOriginalName("testToHexString"));
    verifyMethodHasNoFloatInvokes(mainClass.uniqueMethodWithOriginalName("testToString"));
    verifyMethodHasNoFloatInvokes(mainClass.uniqueMethodWithOriginalName("testStaticToString"));
    verifyMethodHasNoFloatStringValueOfInvokes(
        mainClass.uniqueMethodWithOriginalName("testValueOfString"));
  }

  private void verifyMethodHasNoFloatInvokes(MethodSubject method) {
    assertThat(method, not(invokesMethodWithHolder(Float.class)));
  }

  private void verifyMethodHasNoFloatStringValueOfInvokes(MethodSubject method) {
    assertTrue(
        method
            .streamInstructions()
            .noneMatch(
                i ->
                    i.isInvokeStatic()
                        && i.getMethod().getHolderType().getName().equals("java.lang.Float")
                        && i.getMethod().getName().equals("valueOf")
                        && i.getMethod().getParameter(0).getName().equals("java.lang.String")));
  }

  static class Main {

    @NeverInline
    static void testByteValue() {
      System.out.println(Float.valueOf(3.5f).byteValue());
    }

    @NeverInline
    static void testCompare() {
      System.out.println(Float.compare(1.5f, 2.5f));
      System.out.println(Float.compare(2.5f, 1.5f));
      System.out.println(Float.compare(1.5f, 1.5f));
    }

    @NeverInline
    static void testCompareTo() {
      System.out.println(Float.valueOf(1.5f).compareTo(Float.valueOf(2.5f)));
    }

    @NeverInline
    static void testDoubleValue() {
      System.out.println(Float.valueOf(3.5f).doubleValue());
    }

    @NeverInline
    static void testFloatToIntBits() {
      System.out.println(Float.floatToIntBits(1.0f));
    }

    @NeverInline
    static void testFloatToRawIntBits() {
      System.out.println(Float.floatToRawIntBits(1.0f));
    }

    @NeverInline
    static void testFloatValue() {
      System.out.println(Float.valueOf(3.5f).floatValue());
    }

    @NeverInline
    static void testHashCode() {
      System.out.println(Float.valueOf(3.5f).hashCode());
    }

    @NeverInline
    static void testStaticHashCode() {
      System.out.println(Float.hashCode(3.5f));
    }

    @NeverInline
    static void testIntBitsToFloat() {
      System.out.println(Float.intBitsToFloat(1065353216));
    }

    @NeverInline
    static void testIntValue() {
      System.out.println(Float.valueOf(3.5f).intValue());
    }

    @NeverInline
    static void testIsFinite() {
      System.out.println(Float.isFinite(3.5f));
      System.out.println(Float.isFinite(Float.POSITIVE_INFINITY));
      System.out.println(Float.isFinite(Float.NaN));
    }

    @NeverInline
    static void testIsInfinite() {
      System.out.println(Float.valueOf(Float.POSITIVE_INFINITY).isInfinite());
      System.out.println(Float.valueOf(3.5f).isInfinite());
    }

    @NeverInline
    static void testStaticIsInfinite() {
      System.out.println(Float.isInfinite(Float.NEGATIVE_INFINITY));
      System.out.println(Float.isInfinite(3.5f));
    }

    @NeverInline
    static void testIsNaN() {
      System.out.println(Float.valueOf(Float.NaN).isNaN());
      System.out.println(Float.valueOf(3.5f).isNaN());
    }

    @NeverInline
    static void testStaticIsNaN() {
      System.out.println(Float.isNaN(Float.NaN));
      System.out.println(Float.isNaN(3.5f));
    }

    @NeverInline
    static void testLongValue() {
      System.out.println(Float.valueOf(3.5f).longValue());
    }

    @NeverInline
    static void testMax() {
      System.out.println(Float.max(1.5f, 2.5f));
    }

    @NeverInline
    static void testMin() {
      System.out.println(Float.min(1.5f, 2.5f));
    }

    @NeverInline
    static void testParseFloat() {
      System.out.println(Float.parseFloat("3.5"));
    }

    @NeverInline
    static void testShortValue() {
      System.out.println(Float.valueOf(3.5f).shortValue());
    }

    @NeverInline
    static void testSum() {
      System.out.println(Float.sum(1.5f, 2.5f));
    }

    @NeverInline
    static void testToHexString() {
      System.out.println(Float.toHexString(1.0f));
    }

    @NeverInline
    static void testToString() {
      System.out.println(Float.valueOf(3.5f).toString());
    }

    @NeverInline
    static void testStaticToString() {
      System.out.println(Float.toString(3.5f));
    }

    @NeverInline
    static void testValueOfFloat() {
      System.out.println(Float.valueOf(3.5f));
    }

    @NeverInline
    static void testValueOfString() {
      System.out.println(Float.valueOf("3.5"));
    }

    public static void main(String[] args) {
      testByteValue();
      testCompare();
      testCompareTo();
      testDoubleValue();
      testFloatToIntBits();
      testFloatToRawIntBits();
      testFloatValue();
      testHashCode();
      testStaticHashCode();
      testIntBitsToFloat();
      testIntValue();
      testIsFinite();
      testIsInfinite();
      testStaticIsInfinite();
      testIsNaN();
      testStaticIsNaN();
      testLongValue();
      testMax();
      testMin();
      testParseFloat();
      testShortValue();
      testSum();
      testToHexString();
      testToString();
      testStaticToString();
      testValueOfFloat();
      testValueOfString();
    }
  }
}
