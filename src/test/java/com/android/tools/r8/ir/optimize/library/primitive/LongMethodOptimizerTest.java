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
public class LongMethodOptimizerTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "3", // bitCount
          "42", // byteValue
          "-1", // compare
          "1", // compare
          "0", // compare
          "-1", // compareTo
          "-1", // compareUnsigned
          "42", // decode
          "5", // divideUnsigned
          "42.0", // doubleValue
          "true", // equals
          "false", // equals
          "42.0", // floatValue
          "42", // hashCode
          "42", // static hashCode
          "32", // highestOneBit
          "42", // intValue
          "42", // longValue
          "2", // lowestOneBit
          "20", // max
          "10", // min
          "58", // numberOfLeadingZeros
          "1", // numberOfTrailingZeros
          "42", // parseLong
          "16", // parseLongWithRadix
          "42", // parseUnsignedLong
          "16", // parseUnsignedLongWithRadix
          "2", // remainderUnsigned
          "6052837899185946624", // reverse
          "3026418949592973312", // reverseBytes
          "84", // rotateLeft
          "21", // rotateRight
          "42", // shortValue
          "1", // signum
          "30", // sum
          "101010", // toBinaryString
          "2a", // toHexString
          "52", // toOctalString
          "42", // toString
          "42", // static toString
          "2a", // toStringWithRadix
          "42", // toUnsignedString
          "2a", // toUnsignedStringWithRadix
          "42", // valueOf(long)
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
      verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testByteValue"));
      verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testIntValue"));
      verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testLongValue"));
      verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testShortValue"));
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
      verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testCompare"));
    }
    if (isAtLeastN) {
      verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testStaticHashCode"));
      verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testMax"));
      verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testMin"));
      verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testSum"));
    }
    if (isAtLeastO) {
      verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testCompareUnsigned"));
      verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testDivideUnsigned"));
      verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testParseUnsignedLong"));
      verifyMethodHasNoLongInvokes(
          mainClass.uniqueMethodWithOriginalName("testParseUnsignedLongWithRadix"));
      verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testRemainderUnsigned"));
      verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testToUnsignedString"));
      verifyMethodHasNoLongInvokes(
          mainClass.uniqueMethodWithOriginalName("testToUnsignedStringWithRadix"));
    }

    verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testBitCount"));
    verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testCompareTo"));
    verifyMethodHasNoLongStringValueOfInvokes(mainClass.uniqueMethodWithOriginalName("testDecode"));
    verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testDoubleValue"));
    verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testEquals"));
    verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testFloatValue"));
    verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testHashCode"));
    verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testHighestOneBit"));
    verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testLowestOneBit"));
    verifyMethodHasNoLongInvokes(
        mainClass.uniqueMethodWithOriginalName("testNumberOfLeadingZeros"));
    verifyMethodHasNoLongInvokes(
        mainClass.uniqueMethodWithOriginalName("testNumberOfTrailingZeros"));
    verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testParseLong"));
    verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testParseLongWithRadix"));
    verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testReverse"));
    verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testReverseBytes"));
    verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testRotateLeft"));
    verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testRotateRight"));
    verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testSignum"));
    verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testToBinaryString"));
    verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testToHexString"));
    verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testToOctalString"));
    verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testToString"));
    verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testStaticToString"));
    verifyMethodHasNoLongInvokes(mainClass.uniqueMethodWithOriginalName("testToStringWithRadix"));
    verifyMethodHasNoLongStringValueOfInvokes(
        mainClass.uniqueMethodWithOriginalName("testValueOfString"));
    verifyMethodHasNoLongStringValueOfInvokes(
        mainClass.uniqueMethodWithOriginalName("testValueOfStringWithRadix"));
  }

  private void verifyMethodHasNoLongInvokes(MethodSubject method) {
    assertThat(method, not(invokesMethodWithHolder(Long.class)));
  }

  private void verifyMethodHasNoLongStringValueOfInvokes(MethodSubject method) {
    assertTrue(
        method
            .streamInstructions()
            .noneMatch(
                i ->
                    i.isInvokeStatic()
                        && i.getMethod().getHolderType().getName().equals("java.lang.Long")
                        && (i.getMethod().getName().equals("valueOf")
                            || i.getMethod().getName().equals("decode"))
                        && i.getMethod().getParameter(0).getName().equals("java.lang.String")));
  }

  static class Main {

    @NeverInline
    static void testBitCount() {
      System.out.println(Long.bitCount(42L));
    }

    @NeverInline
    static void testByteValue() {
      System.out.println(Long.valueOf(42L).byteValue());
    }

    @NeverInline
    static void testCompare() {
      System.out.println(Long.compare(10L, 20L));
      System.out.println(Long.compare(20L, 10L));
      System.out.println(Long.compare(10L, 10L));
    }

    @NeverInline
    static void testCompareTo() {
      System.out.println(Long.valueOf(10L).compareTo(Long.valueOf(20L)));
    }

    @NeverInline
    static void testCompareUnsigned() {
      System.out.println(Long.compareUnsigned(10L, -10L));
    }

    @NeverInline
    static void testDecode() {
      System.out.println(Long.decode("42"));
    }

    @NeverInline
    static void testDivideUnsigned() {
      System.out.println(Long.divideUnsigned(42L, 8L));
    }

    @NeverInline
    static void testDoubleValue() {
      System.out.println(Long.valueOf(42L).doubleValue());
    }

    @NeverInline
    static void testEquals() {
      System.out.println(Long.valueOf(42L).equals(Long.valueOf(42L)));
      System.out.println(Long.valueOf(42L).equals(Long.valueOf(43L)));
    }

    @NeverInline
    static void testFloatValue() {
      System.out.println(Long.valueOf(42L).floatValue());
    }

    @NeverInline
    static void testHashCode() {
      System.out.println(Long.valueOf(42L).hashCode());
    }

    @NeverInline
    static void testStaticHashCode() {
      System.out.println(Long.hashCode(42L));
    }

    @NeverInline
    static void testHighestOneBit() {
      System.out.println(Long.highestOneBit(42L));
    }

    @NeverInline
    static void testIntValue() {
      System.out.println(Long.valueOf(42L).intValue());
    }

    @NeverInline
    static void testLongValue() {
      System.out.println(Long.valueOf(42L).longValue());
    }

    @NeverInline
    static void testLowestOneBit() {
      System.out.println(Long.lowestOneBit(42L));
    }

    @NeverInline
    static void testMax() {
      System.out.println(Long.max(10L, 20L));
    }

    @NeverInline
    static void testMin() {
      System.out.println(Long.min(10L, 20L));
    }

    @NeverInline
    static void testNumberOfLeadingZeros() {
      System.out.println(Long.numberOfLeadingZeros(42L));
    }

    @NeverInline
    static void testNumberOfTrailingZeros() {
      System.out.println(Long.numberOfTrailingZeros(42L));
    }

    @NeverInline
    static void testParseLong() {
      System.out.println(Long.parseLong("42"));
    }

    @NeverInline
    static void testParseLongWithRadix() {
      System.out.println(Long.parseLong("10", 16));
    }

    @NeverInline
    static void testParseUnsignedLong() {
      System.out.println(Long.parseUnsignedLong("42"));
    }

    @NeverInline
    static void testParseUnsignedLongWithRadix() {
      System.out.println(Long.parseUnsignedLong("10", 16));
    }

    @NeverInline
    static void testRemainderUnsigned() {
      System.out.println(Long.remainderUnsigned(42L, 8L));
    }

    @NeverInline
    static void testReverse() {
      System.out.println(Long.reverse(42L));
    }

    @NeverInline
    static void testReverseBytes() {
      System.out.println(Long.reverseBytes(42L));
    }

    @NeverInline
    static void testRotateLeft() {
      System.out.println(Long.rotateLeft(42L, 1));
    }

    @NeverInline
    static void testRotateRight() {
      System.out.println(Long.rotateRight(42L, 1));
    }

    @NeverInline
    static void testShortValue() {
      System.out.println(Long.valueOf(42L).shortValue());
    }

    @NeverInline
    static void testSignum() {
      System.out.println(Long.signum(42L));
    }

    @NeverInline
    static void testSum() {
      System.out.println(Long.sum(10L, 20L));
    }

    @NeverInline
    static void testToBinaryString() {
      System.out.println(Long.toBinaryString(42L));
    }

    @NeverInline
    static void testToHexString() {
      System.out.println(Long.toHexString(42L));
    }

    @NeverInline
    static void testToOctalString() {
      System.out.println(Long.toOctalString(42L));
    }

    @NeverInline
    static void testToString() {
      System.out.println(Long.valueOf(42L).toString());
    }

    @NeverInline
    static void testStaticToString() {
      System.out.println(Long.toString(42L));
    }

    @NeverInline
    static void testToStringWithRadix() {
      System.out.println(Long.toString(42L, 16));
    }

    @NeverInline
    static void testToUnsignedString() {
      System.out.println(Long.toUnsignedString(42L));
    }

    @NeverInline
    static void testToUnsignedStringWithRadix() {
      System.out.println(Long.toUnsignedString(42L, 16));
    }

    @NeverInline
    static void testValueOfLong() {
      System.out.println(Long.valueOf(42L));
    }

    @NeverInline
    static void testValueOfString() {
      System.out.println(Long.valueOf("42"));
    }

    @NeverInline
    static void testValueOfStringWithRadix() {
      System.out.println(Long.valueOf("10", 16));
    }

    public static void main(String[] args) {
      testBitCount();
      testByteValue();
      testCompare();
      testCompareTo();
      testCompareUnsigned();
      testDecode();
      testDivideUnsigned();
      testDoubleValue();
      testEquals();
      testFloatValue();
      testHashCode();
      testStaticHashCode();
      testHighestOneBit();
      testIntValue();
      testLongValue();
      testLowestOneBit();
      testMax();
      testMin();
      testNumberOfLeadingZeros();
      testNumberOfTrailingZeros();
      testParseLong();
      testParseLongWithRadix();
      testParseUnsignedLong();
      testParseUnsignedLongWithRadix();
      testRemainderUnsigned();
      testReverse();
      testReverseBytes();
      testRotateLeft();
      testRotateRight();
      testShortValue();
      testSignum();
      testSum();
      testToBinaryString();
      testToHexString();
      testToOctalString();
      testToString();
      testStaticToString();
      testToStringWithRadix();
      testToUnsignedString();
      testToUnsignedStringWithRadix();
      testValueOfLong();
      testValueOfString();
      testValueOfStringWithRadix();
    }
  }
}
