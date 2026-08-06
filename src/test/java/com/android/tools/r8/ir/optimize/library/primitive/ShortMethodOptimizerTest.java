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
public class ShortMethodOptimizerTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "42", // byteValue
          "-10", // compare
          "10", // compare
          "0", // compare
          "-10", // compareTo
          "42", // decode
          "42.0", // doubleValue
          "true", // equals
          "false", // equals
          "42.0", // floatValue
          "42", // hashCode
          "42", // static hashCode
          "42", // intValue
          "42", // longValue
          "42", // parseShort
          "16", // parseShortWithRadix
          "513", // reverseBytes
          "42", // shortValue
          "42", // toString
          "42", // static toString
          "65535", // toUnsignedInt
          "65535", // toUnsignedLong
          "42", // valueOf(short)
          "42", // valueOf(String)
          "16"); // valueOf(String, int)

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
      verifyMethodHasNoShortInvokes(mainClass.uniqueMethodWithOriginalName("testByteValue"));
      verifyMethodHasNoShortInvokes(mainClass.uniqueMethodWithOriginalName("testIntValue"));
      verifyMethodHasNoShortInvokes(mainClass.uniqueMethodWithOriginalName("testShortValue"));
    }
    boolean isAtLeastK =
        parameters.isCfRuntime()
            || parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.K);
    boolean isAtLeastN =
        parameters.isCfRuntime()
            || parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N);
    boolean isAtLeastO =
        parameters.isCfRuntime()
            || parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.O);

    if (isAtLeastK) {
      verifyMethodHasNoShortInvokes(mainClass.uniqueMethodWithOriginalName("testCompare"));
    }
    if (isAtLeastN) {
      verifyMethodHasNoShortInvokes(mainClass.uniqueMethodWithOriginalName("testStaticHashCode"));
    }
    if (isAtLeastO) {
      verifyMethodHasNoShortInvokes(mainClass.uniqueMethodWithOriginalName("testToUnsignedInt"));
      verifyMethodHasNoShortInvokes(mainClass.uniqueMethodWithOriginalName("testToUnsignedLong"));
    }

    verifyMethodHasNoShortInvokes(mainClass.uniqueMethodWithOriginalName("testCompareTo"));
    verifyMethodHasNoShortStringValueOfInvokes(
        mainClass.uniqueMethodWithOriginalName("testDecode"));
    verifyMethodHasNoShortInvokes(mainClass.uniqueMethodWithOriginalName("testDoubleValue"));
    verifyMethodHasNoShortInvokes(mainClass.uniqueMethodWithOriginalName("testEquals"));
    verifyMethodHasNoShortInvokes(mainClass.uniqueMethodWithOriginalName("testFloatValue"));
    verifyMethodHasNoShortInvokes(mainClass.uniqueMethodWithOriginalName("testHashCode"));
    verifyMethodHasNoShortInvokes(mainClass.uniqueMethodWithOriginalName("testLongValue"));
    verifyMethodHasNoShortInvokes(mainClass.uniqueMethodWithOriginalName("testParseShort"));
    verifyMethodHasNoShortInvokes(
        mainClass.uniqueMethodWithOriginalName("testParseShortWithRadix"));
    verifyMethodHasNoShortInvokes(mainClass.uniqueMethodWithOriginalName("testReverseBytes"));
    verifyMethodHasNoShortInvokes(mainClass.uniqueMethodWithOriginalName("testToString"));
    verifyMethodHasNoShortInvokes(mainClass.uniqueMethodWithOriginalName("testStaticToString"));
    verifyMethodHasNoShortStringValueOfInvokes(
        mainClass.uniqueMethodWithOriginalName("testValueOfString"));
    verifyMethodHasNoShortStringValueOfInvokes(
        mainClass.uniqueMethodWithOriginalName("testValueOfStringWithRadix"));
  }

  private void verifyMethodHasNoShortInvokes(MethodSubject method) {
    assertThat(method, not(invokesMethodWithHolder(Short.class)));
  }

  private void verifyMethodHasNoShortStringValueOfInvokes(MethodSubject method) {
    assertTrue(
        method
            .streamInstructions()
            .noneMatch(
                i ->
                    i.isInvokeStatic()
                        && i.getMethod().getHolderType().getName().equals("java.lang.Short")
                        && (i.getMethod().getName().equals("valueOf")
                            || i.getMethod().getName().equals("decode"))
                        && i.getMethod().getParameter(0).getName().equals("java.lang.String")));
  }

  static class Main {

    @NeverInline
    static void testByteValue() {
      System.out.println(Short.valueOf((short) 42).byteValue());
    }

    @NeverInline
    static void testCompare() {
      System.out.println(Short.compare((short) 10, (short) 20));
      System.out.println(Short.compare((short) 20, (short) 10));
      System.out.println(Short.compare((short) 10, (short) 10));
    }

    @NeverInline
    static void testCompareTo() {
      System.out.println(Short.valueOf((short) 10).compareTo(Short.valueOf((short) 20)));
    }

    @NeverInline
    static void testDecode() {
      System.out.println(Short.decode("42"));
    }

    @NeverInline
    static void testDoubleValue() {
      System.out.println(Short.valueOf((short) 42).doubleValue());
    }

    @NeverInline
    static void testEquals() {
      System.out.println(Short.valueOf((short) 42).equals(Short.valueOf((short) 42)));
      System.out.println(Short.valueOf((short) 42).equals(Short.valueOf((short) 43)));
    }

    @NeverInline
    static void testFloatValue() {
      System.out.println(Short.valueOf((short) 42).floatValue());
    }

    @NeverInline
    static void testHashCode() {
      System.out.println(Short.valueOf((short) 42).hashCode());
    }

    @NeverInline
    static void testStaticHashCode() {
      System.out.println(Short.hashCode((short) 42));
    }

    @NeverInline
    static void testIntValue() {
      System.out.println(Short.valueOf((short) 42).intValue());
    }

    @NeverInline
    static void testLongValue() {
      System.out.println(Short.valueOf((short) 42).longValue());
    }

    @NeverInline
    static void testParseShort() {
      System.out.println(Short.parseShort("42"));
    }

    @NeverInline
    static void testParseShortWithRadix() {
      System.out.println(Short.parseShort("10", 16));
    }

    @NeverInline
    static void testReverseBytes() {
      System.out.println(Short.reverseBytes((short) 0x0102));
    }

    @NeverInline
    static void testShortValue() {
      System.out.println(Short.valueOf((short) 42).shortValue());
    }

    @NeverInline
    static void testToString() {
      System.out.println(Short.valueOf((short) 42).toString());
    }

    @NeverInline
    static void testStaticToString() {
      System.out.println(Short.toString((short) 42));
    }

    @NeverInline
    static void testToUnsignedInt() {
      System.out.println(Short.toUnsignedInt((short) -1));
    }

    @NeverInline
    static void testToUnsignedLong() {
      System.out.println(Short.toUnsignedLong((short) -1));
    }

    @NeverInline
    static void testValueOfShort() {
      System.out.println(Short.valueOf((short) 42));
    }

    @NeverInline
    static void testValueOfString() {
      System.out.println(Short.valueOf("42"));
    }

    @NeverInline
    static void testValueOfStringWithRadix() {
      System.out.println(Short.valueOf("10", 16));
    }

    public static void main(String[] args) {
      testByteValue();
      testCompare();
      testCompareTo();
      testDecode();
      testDoubleValue();
      testEquals();
      testFloatValue();
      testHashCode();
      testStaticHashCode();
      testIntValue();
      testLongValue();
      testParseShort();
      testParseShortWithRadix();
      testReverseBytes();
      testShortValue();
      testToString();
      testStaticToString();
      testToUnsignedInt();
      testToUnsignedLong();
      testValueOfShort();
      testValueOfString();
      testValueOfStringWithRadix();
    }
  }
}
