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
import com.android.tools.r8.ToolHelper;
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
public class IntegerMethodOptimizerTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "2", // bitCount
          "100", // byteValue
          "-1", // compare
          "1", // compare
          "0", // compare
          "-1", // compareTo
          "1", // compareUnsigned
          "42", // decode
          "2147483647", // divideUnsigned
          "42.0", // doubleValue
          "true", // equals
          "false", // equals
          "42.0", // floatValue
          "42", // hashCode
          "42", // static hashCode
          "8", // highestOneBit
          "42", // intValue
          "42", // longValue
          "2", // lowestOneBit
          "20", // max
          "10", // min
          "31", // numberOfLeadingZeros
          "3", // numberOfTrailingZeros
          "42", // parseInt
          "10", // parseIntWithRadix
          "42", // parseUnsignedInt
          "255", // parseUnsignedIntWithRadix
          "1", // remainderUnsigned
          "-2147483648", // reverse
          "2018915346", // reverseBytes
          "4", // rotateLeft
          "1", // rotateRight
          "42", // shortValue
          "-1", // signum
          "30", // sum
          "1010", // toBinaryString
          "ff", // toHexString
          "12", // toOctalString
          "42", // toString
          "42", // static toString
          "ff", // toStringWithRadix
          "4294967295", // toUnsignedLong
          "4294967295", // toUnsignedString
          "ffffffff", // toUnsignedStringWithRadix
          "42", // valueOf(int)
          "42", // valueOf(String)
          "255"); // valueOf(String, int)

  @Test
  public void testD8Release() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addInnerClasses(getClass())
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .addOptionsModification(options -> options.apiModelingOptions().disableOutlining())
        .disableDesugaring()
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
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .addKeepMainRule(Main.class)
        .addOptionsModification(options -> options.apiModelingOptions().disableOutlining())
        .disableDesugaring()
        .enableInliningAnnotations()
        .compile()
        .inspect(inspector -> inspect(inspector, true))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private void inspect(CodeInspector inspector, boolean isR8) {
    ClassSubject mainClass = inspector.clazz(Main.class);
    if (isR8) {
      verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testIntValue"));
    }
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testBitCount"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testByteValue"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testCompare"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testCompareTo"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testCompareUnsigned"));
    verifyMethodHasNoIntegerStringValueOfInvokes(
        mainClass.uniqueMethodWithOriginalName("testDecode"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testDivideUnsigned"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testDoubleValue"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testEquals"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testFloatValue"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testHashCode"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testStaticHashCode"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testHighestOneBit"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testLongValue"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testLowestOneBit"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testMax"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testMin"));
    verifyMethodHasNoIntegerInvokes(
        mainClass.uniqueMethodWithOriginalName("testNumberOfLeadingZeros"));
    verifyMethodHasNoIntegerInvokes(
        mainClass.uniqueMethodWithOriginalName("testNumberOfTrailingZeros"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testParseInt"));
    verifyMethodHasNoIntegerInvokes(
        mainClass.uniqueMethodWithOriginalName("testParseIntWithRadix"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testParseUnsignedInt"));
    verifyMethodHasNoIntegerInvokes(
        mainClass.uniqueMethodWithOriginalName("testParseUnsignedIntWithRadix"));
    verifyMethodHasNoIntegerInvokes(
        mainClass.uniqueMethodWithOriginalName("testRemainderUnsigned"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testReverse"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testReverseBytes"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testRotateLeft"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testRotateRight"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testShortValue"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testSignum"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testSum"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testToBinaryString"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testToHexString"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testToOctalString"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testToString"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testStaticToString"));
    verifyMethodHasNoIntegerInvokes(
        mainClass.uniqueMethodWithOriginalName("testToStringWithRadix"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testToUnsignedLong"));
    verifyMethodHasNoIntegerInvokes(mainClass.uniqueMethodWithOriginalName("testToUnsignedString"));
    verifyMethodHasNoIntegerInvokes(
        mainClass.uniqueMethodWithOriginalName("testToUnsignedStringWithRadix"));
    verifyMethodHasNoIntegerStringValueOfInvokes(
        mainClass.uniqueMethodWithOriginalName("testValueOfString"));
    verifyMethodHasNoIntegerStringValueOfInvokes(
        mainClass.uniqueMethodWithOriginalName("testValueOfStringWithRadix"));
  }

  private void verifyMethodHasNoIntegerInvokes(MethodSubject method) {
    assertThat(method, not(invokesMethodWithHolder(Integer.class)));
  }

  private void verifyMethodHasNoIntegerStringValueOfInvokes(MethodSubject method) {
    assertTrue(
        method
            .streamInstructions()
            .noneMatch(
                i ->
                    i.isInvokeStatic()
                        && i.getMethod().getHolderType().getName().equals("java.lang.Integer")
                        && (i.getMethod().getName().equals("valueOf")
                            || i.getMethod().getName().equals("decode"))
                        && i.getMethod().getParameter(0).getName().equals("java.lang.String")));
  }

  static class Main {

    @NeverInline
    static void testBitCount() {
      System.out.println(Integer.bitCount(10));
    }

    @NeverInline
    static void testByteValue() {
      System.out.println(Integer.valueOf(100).byteValue());
    }

    @NeverInline
    static void testCompare() {
      System.out.println(Integer.compare(10, 20));
      System.out.println(Integer.compare(20, 10));
      System.out.println(Integer.compare(10, 10));
    }

    @NeverInline
    static void testCompareTo() {
      System.out.println(Integer.valueOf(10).compareTo(Integer.valueOf(20)));
    }

    @NeverInline
    static void testCompareUnsigned() {
      System.out.println(Integer.compareUnsigned(-1, 1));
    }

    @NeverInline
    static void testDecode() {
      System.out.println(Integer.decode("42"));
    }

    @NeverInline
    static void testDivideUnsigned() {
      System.out.println(Integer.divideUnsigned(-1, 2));
    }

    @NeverInline
    static void testDoubleValue() {
      System.out.println(Integer.valueOf(42).doubleValue());
    }

    @NeverInline
    static void testEquals() {
      System.out.println(Integer.valueOf(42).equals(Integer.valueOf(42)));
      System.out.println(Integer.valueOf(42).equals(Integer.valueOf(43)));
    }

    @NeverInline
    static void testFloatValue() {
      System.out.println(Integer.valueOf(42).floatValue());
    }

    @NeverInline
    static void testHashCode() {
      System.out.println(Integer.valueOf(42).hashCode());
    }

    @NeverInline
    static void testStaticHashCode() {
      System.out.println(Integer.hashCode(42));
    }

    @NeverInline
    static void testHighestOneBit() {
      System.out.println(Integer.highestOneBit(10));
    }

    @NeverInline
    static void testIntValue() {
      System.out.println(Integer.valueOf(42).intValue());
    }

    @NeverInline
    static void testLongValue() {
      System.out.println(Integer.valueOf(42).longValue());
    }

    @NeverInline
    static void testLowestOneBit() {
      System.out.println(Integer.lowestOneBit(10));
    }

    @NeverInline
    static void testMax() {
      System.out.println(Integer.max(10, 20));
    }

    @NeverInline
    static void testMin() {
      System.out.println(Integer.min(10, 20));
    }

    @NeverInline
    static void testNumberOfLeadingZeros() {
      System.out.println(Integer.numberOfLeadingZeros(1));
    }

    @NeverInline
    static void testNumberOfTrailingZeros() {
      System.out.println(Integer.numberOfTrailingZeros(8));
    }

    @NeverInline
    static void testParseInt() {
      System.out.println(Integer.parseInt("42"));
    }

    @NeverInline
    static void testParseIntWithRadix() {
      System.out.println(Integer.parseInt("1010", 2));
    }

    @NeverInline
    static void testParseUnsignedInt() {
      System.out.println(Integer.parseUnsignedInt("42"));
    }

    @NeverInline
    static void testParseUnsignedIntWithRadix() {
      System.out.println(Integer.parseUnsignedInt("ff", 16));
    }

    @NeverInline
    static void testRemainderUnsigned() {
      System.out.println(Integer.remainderUnsigned(10, 3));
    }

    @NeverInline
    static void testReverse() {
      System.out.println(Integer.reverse(1));
    }

    @NeverInline
    static void testReverseBytes() {
      System.out.println(Integer.reverseBytes(0x12345678));
    }

    @NeverInline
    static void testRotateLeft() {
      System.out.println(Integer.rotateLeft(1, 2));
    }

    @NeverInline
    static void testRotateRight() {
      System.out.println(Integer.rotateRight(4, 2));
    }

    @NeverInline
    static void testShortValue() {
      System.out.println(Integer.valueOf(42).shortValue());
    }

    @NeverInline
    static void testSignum() {
      System.out.println(Integer.signum(-10));
    }

    @NeverInline
    static void testSum() {
      System.out.println(Integer.sum(10, 20));
    }

    @NeverInline
    static void testToBinaryString() {
      System.out.println(Integer.toBinaryString(10));
    }

    @NeverInline
    static void testToHexString() {
      System.out.println(Integer.toHexString(255));
    }

    @NeverInline
    static void testToOctalString() {
      System.out.println(Integer.toOctalString(10));
    }

    @NeverInline
    static void testToString() {
      System.out.println(Integer.valueOf(42).toString());
    }

    @NeverInline
    static void testStaticToString() {
      System.out.println(Integer.toString(42));
    }

    @NeverInline
    static void testToStringWithRadix() {
      System.out.println(Integer.toString(255, 16));
    }

    @NeverInline
    static void testToUnsignedLong() {
      System.out.println(Integer.toUnsignedLong(-1));
    }

    @NeverInline
    static void testToUnsignedString() {
      System.out.println(Integer.toUnsignedString(-1));
    }

    @NeverInline
    static void testToUnsignedStringWithRadix() {
      System.out.println(Integer.toUnsignedString(-1, 16));
    }

    @NeverInline
    static void testValueOfInt() {
      System.out.println(Integer.valueOf(42));
    }

    @NeverInline
    static void testValueOfString() {
      System.out.println(Integer.valueOf("42"));
    }

    @NeverInline
    static void testValueOfStringWithRadix() {
      System.out.println(Integer.valueOf("ff", 16));
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
      testParseInt();
      testParseIntWithRadix();
      testParseUnsignedInt();
      testParseUnsignedIntWithRadix();
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
      testToUnsignedLong();
      testToUnsignedString();
      testToUnsignedStringWithRadix();
      testValueOfInt();
      testValueOfString();
      testValueOfStringWithRadix();
    }
  }
}
