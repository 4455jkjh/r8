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
public class ByteMethodOptimizerTest extends TestBase {

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
          "42", // parseByte
          "16", // parseByteWithRadix
          "42", // shortValue
          "42", // toString
          "42", // static toString
          "255", // toUnsignedInt
          "255", // toUnsignedLong
          "42", // valueOf(byte)
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
      verifyMethodHasNoByteInvokes(mainClass.uniqueMethodWithOriginalName("testByteValue"));
      verifyMethodHasNoByteInvokes(mainClass.uniqueMethodWithOriginalName("testIntValue"));
    }
    verifyMethodHasNoByteInvokes(mainClass.uniqueMethodWithOriginalName("testCompare"));
    verifyMethodHasNoByteInvokes(mainClass.uniqueMethodWithOriginalName("testCompareTo"));
    verifyMethodHasNoByteStringValueOfInvokes(mainClass.uniqueMethodWithOriginalName("testDecode"));
    verifyMethodHasNoByteInvokes(mainClass.uniqueMethodWithOriginalName("testDoubleValue"));
    verifyMethodHasNoByteInvokes(mainClass.uniqueMethodWithOriginalName("testEquals"));
    verifyMethodHasNoByteInvokes(mainClass.uniqueMethodWithOriginalName("testFloatValue"));
    verifyMethodHasNoByteInvokes(mainClass.uniqueMethodWithOriginalName("testHashCode"));
    verifyMethodHasNoByteInvokes(mainClass.uniqueMethodWithOriginalName("testStaticHashCode"));
    verifyMethodHasNoByteInvokes(mainClass.uniqueMethodWithOriginalName("testLongValue"));
    verifyMethodHasNoByteInvokes(mainClass.uniqueMethodWithOriginalName("testParseByte"));
    verifyMethodHasNoByteInvokes(mainClass.uniqueMethodWithOriginalName("testParseByteWithRadix"));
    verifyMethodHasNoByteInvokes(mainClass.uniqueMethodWithOriginalName("testShortValue"));
    verifyMethodHasNoByteInvokes(mainClass.uniqueMethodWithOriginalName("testToString"));
    verifyMethodHasNoByteInvokes(mainClass.uniqueMethodWithOriginalName("testStaticToString"));
    verifyMethodHasNoByteInvokes(mainClass.uniqueMethodWithOriginalName("testToUnsignedInt"));
    verifyMethodHasNoByteInvokes(mainClass.uniqueMethodWithOriginalName("testToUnsignedLong"));
    verifyMethodHasNoByteStringValueOfInvokes(
        mainClass.uniqueMethodWithOriginalName("testValueOfString"));
    verifyMethodHasNoByteStringValueOfInvokes(
        mainClass.uniqueMethodWithOriginalName("testValueOfStringWithRadix"));
  }

  private void verifyMethodHasNoByteInvokes(MethodSubject method) {
    assertThat(method, not(invokesMethodWithHolder(Byte.class)));
  }

  private void verifyMethodHasNoByteStringValueOfInvokes(MethodSubject method) {
    assertTrue(
        method
            .streamInstructions()
            .noneMatch(
                i ->
                    i.isInvokeStatic()
                        && i.getMethod().getHolderType().getName().equals("java.lang.Byte")
                        && (i.getMethod().getName().equals("valueOf")
                            || i.getMethod().getName().equals("decode"))
                        && i.getMethod().getParameter(0).getName().equals("java.lang.String")));
  }

  static class Main {

    @NeverInline
    static void testByteValue() {
      System.out.println(Byte.valueOf((byte) 42).byteValue());
    }

    @NeverInline
    static void testCompare() {
      System.out.println(Byte.compare((byte) 10, (byte) 20));
      System.out.println(Byte.compare((byte) 20, (byte) 10));
      System.out.println(Byte.compare((byte) 10, (byte) 10));
    }

    @NeverInline
    static void testCompareTo() {
      System.out.println(Byte.valueOf((byte) 10).compareTo(Byte.valueOf((byte) 20)));
    }

    @NeverInline
    static void testDecode() {
      System.out.println(Byte.decode("42"));
    }

    @NeverInline
    static void testDoubleValue() {
      System.out.println(Byte.valueOf((byte) 42).doubleValue());
    }

    @NeverInline
    static void testEquals() {
      System.out.println(Byte.valueOf((byte) 42).equals(Byte.valueOf((byte) 42)));
      System.out.println(Byte.valueOf((byte) 42).equals(Byte.valueOf((byte) 43)));
    }

    @NeverInline
    static void testFloatValue() {
      System.out.println(Byte.valueOf((byte) 42).floatValue());
    }

    @NeverInline
    static void testHashCode() {
      System.out.println(Byte.valueOf((byte) 42).hashCode());
    }

    @NeverInline
    static void testStaticHashCode() {
      System.out.println(Byte.hashCode((byte) 42));
    }

    @NeverInline
    static void testIntValue() {
      System.out.println(Byte.valueOf((byte) 42).intValue());
    }

    @NeverInline
    static void testLongValue() {
      System.out.println(Byte.valueOf((byte) 42).longValue());
    }

    @NeverInline
    static void testParseByte() {
      System.out.println(Byte.parseByte("42"));
    }

    @NeverInline
    static void testParseByteWithRadix() {
      System.out.println(Byte.parseByte("10", 16));
    }

    @NeverInline
    static void testShortValue() {
      System.out.println(Byte.valueOf((byte) 42).shortValue());
    }

    @NeverInline
    static void testToString() {
      System.out.println(Byte.valueOf((byte) 42).toString());
    }

    @NeverInline
    static void testStaticToString() {
      System.out.println(Byte.toString((byte) 42));
    }

    @NeverInline
    static void testToUnsignedInt() {
      System.out.println(Byte.toUnsignedInt((byte) -1));
    }

    @NeverInline
    static void testToUnsignedLong() {
      System.out.println(Byte.toUnsignedLong((byte) -1));
    }

    @NeverInline
    static void testValueOfByte() {
      System.out.println(Byte.valueOf((byte) 42));
    }

    @NeverInline
    static void testValueOfString() {
      System.out.println(Byte.valueOf("42"));
    }

    @NeverInline
    static void testValueOfStringWithRadix() {
      System.out.println(Byte.valueOf("10", 16));
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
      testParseByte();
      testParseByteWithRadix();
      testShortValue();
      testToString();
      testStaticToString();
      testToUnsignedInt();
      testToUnsignedLong();
      testValueOfByte();
      testValueOfString();
      testValueOfStringWithRadix();
    }
  }
}
