// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithHolder;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

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
public class JavaLangMathMethodOptimizerTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "3.5", // absDouble
          "3.5", // absFloat
          "3", // absInt
          "3", // absLong
          "0.0", // acos
          "5", // addExactInt
          "5", // addExactLong
          "0.0", // asin
          "0.0", // atan
          "0.0", // atan2
          "2.0", // cbrt
          "4.0", // ceil
          "3.5", // copySignDouble
          "3.5", // copySignFloat
          "1.0", // cos
          "1.0", // cosh
          "2", // decrementExactInt
          "2", // decrementExactLong
          "1.0", // exp
          "0.0", // expm1
          "3.0", // floor
          "2", // floorDivInt
          "2", // floorDivLong
          "1", // floorModInt
          "1", // floorModLong
          "1", // getExponentDouble
          "1", // getExponentFloat
          "5.0", // hypot
          "0.0", // IEEEremainder
          "4", // incrementExactInt
          "4", // incrementExactLong
          "0.0", // log
          "1.0", // log10
          "0.0", // log1p
          "3.5", // maxDouble
          "3.5", // maxFloat
          "3", // maxInt
          "3", // maxLong
          "1.5", // minDouble
          "1.5", // minFloat
          "1", // minInt
          "1", // minLong
          "6", // multiplyExactInt
          "6", // multiplyExactLong
          "-3", // negateExactInt
          "-3", // negateExactLong
          "1.0", // nextAfterDouble
          "1.0", // nextAfterFloat
          "0.9999999999999999", // nextDownDouble
          "0.99999994", // nextDownFloat
          "1.0000000000000002", // nextUpDouble
          "1.0000001", // nextUpFloat
          "8.0", // pow
          "4.0", // rint
          "4", // roundDouble
          "4", // roundFloat
          "4.0", // scalbDouble
          "4.0", // scalbFloat
          "1.0", // signumDouble
          "1.0", // signumFloat
          "0.0", // sin
          "0.0", // sinh
          "2.0", // sqrt
          "1", // subtractExactInt
          "1", // subtractExactLong
          "0.0", // tan
          "0.0", // tanh
          "180.0", // toDegrees
          "3", // toIntExact
          "0.0", // toRadians
          "2.220446049250313E-16", // ulpDouble
          "1.1920929E-7"); // ulpFloat

  @Test
  public void testD8Release() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addInnerClasses(getClass())
        .release()
        .compile()
        .inspect(inspector -> inspect(inspector))
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
        .inspect(inspector -> inspect(inspector))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject mainClass = inspector.clazz(Main.class);
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testAbsDouble"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testAbsFloat"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testAbsInt"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testAbsLong"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testAcos"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testAddExactInt"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testAddExactLong"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testAsin"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testAtan"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testAtan2"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testCbrt"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testCeil"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testCopySignDouble"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testCopySignFloat"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testCos"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testCosh"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testDecrementExactInt"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testDecrementExactLong"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testExp"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testExpm1"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testFloor"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testFloorDivInt"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testFloorDivLong"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testFloorModInt"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testFloorModLong"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testGetExponentDouble"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testGetExponentFloat"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testHypot"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testIEEEremainder"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testIncrementExactInt"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testIncrementExactLong"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testLog"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testLog10"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testLog1p"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testMaxDouble"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testMaxFloat"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testMaxInt"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testMaxLong"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testMinDouble"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testMinFloat"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testMinInt"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testMinLong"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testMultiplyExactInt"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testMultiplyExactLong"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testNegateExactInt"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testNegateExactLong"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testNextAfterDouble"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testNextAfterFloat"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testNextDownDouble"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testNextDownFloat"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testNextUpDouble"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testNextUpFloat"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testPow"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testRint"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testRoundDouble"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testRoundFloat"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testScalbDouble"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testScalbFloat"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testSignumDouble"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testSignumFloat"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testSin"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testSinh"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testSqrt"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testSubtractExactInt"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testSubtractExactLong"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testTan"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testTanh"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testToDegrees"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testToIntExact"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testToRadians"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testUlpDouble"));
    verifyMethodHasNoMathInvokes(mainClass.uniqueMethodWithOriginalName("testUlpFloat"));
  }

  private void verifyMethodHasNoMathInvokes(MethodSubject method) {
    assertThat(method, not(invokesMethodWithHolder(Math.class)));
  }

  static class Main {

    @NeverInline
    static void testAbsDouble() {
      System.out.println(Math.abs(-3.5d));
    }

    @NeverInline
    static void testAbsFloat() {
      System.out.println(Math.abs(-3.5f));
    }

    @NeverInline
    static void testAbsInt() {
      System.out.println(Math.abs(-3));
    }

    @NeverInline
    static void testAbsLong() {
      System.out.println(Math.abs(-3L));
    }

    @NeverInline
    static void testAcos() {
      System.out.println(Math.acos(1.0d));
    }

    @NeverInline
    static void testAddExactInt() {
      System.out.println(Math.addExact(2, 3));
    }

    @NeverInline
    static void testAddExactLong() {
      System.out.println(Math.addExact(2L, 3L));
    }

    @NeverInline
    static void testAsin() {
      System.out.println(Math.asin(0.0d));
    }

    @NeverInline
    static void testAtan() {
      System.out.println(Math.atan(0.0d));
    }

    @NeverInline
    static void testAtan2() {
      System.out.println(Math.atan2(0.0d, 1.0d));
    }

    @NeverInline
    static void testCbrt() {
      System.out.println(Math.cbrt(8.0d));
    }

    @NeverInline
    static void testCeil() {
      System.out.println(Math.ceil(3.5d));
    }

    @NeverInline
    static void testCopySignDouble() {
      System.out.println(Math.copySign(-3.5d, 1.0d));
    }

    @NeverInline
    static void testCopySignFloat() {
      System.out.println(Math.copySign(-3.5f, 1.0f));
    }

    @NeverInline
    static void testCos() {
      System.out.println(Math.cos(0.0d));
    }

    @NeverInline
    static void testCosh() {
      System.out.println(Math.cosh(0.0d));
    }

    @NeverInline
    static void testDecrementExactInt() {
      System.out.println(Math.decrementExact(3));
    }

    @NeverInline
    static void testDecrementExactLong() {
      System.out.println(Math.decrementExact(3L));
    }

    @NeverInline
    static void testExp() {
      System.out.println(Math.exp(0.0d));
    }

    @NeverInline
    static void testExpm1() {
      System.out.println(Math.expm1(0.0d));
    }

    @NeverInline
    static void testFloor() {
      System.out.println(Math.floor(3.5d));
    }

    @NeverInline
    static void testFloorDivInt() {
      System.out.println(Math.floorDiv(5, 2));
    }

    @NeverInline
    static void testFloorDivLong() {
      System.out.println(Math.floorDiv(5L, 2L));
    }

    @NeverInline
    static void testFloorModInt() {
      System.out.println(Math.floorMod(5, 2));
    }

    @NeverInline
    static void testFloorModLong() {
      System.out.println(Math.floorMod(5L, 2L));
    }

    @NeverInline
    static void testGetExponentDouble() {
      System.out.println(Math.getExponent(3.5d));
    }

    @NeverInline
    static void testGetExponentFloat() {
      System.out.println(Math.getExponent(3.5f));
    }

    @NeverInline
    static void testHypot() {
      System.out.println(Math.hypot(3.0d, 4.0d));
    }

    @NeverInline
    static void testIEEEremainder() {
      System.out.println(Math.IEEEremainder(4.0d, 2.0d));
    }

    @NeverInline
    static void testIncrementExactInt() {
      System.out.println(Math.incrementExact(3));
    }

    @NeverInline
    static void testIncrementExactLong() {
      System.out.println(Math.incrementExact(3L));
    }

    @NeverInline
    static void testLog() {
      System.out.println(Math.log(1.0d));
    }

    @NeverInline
    static void testLog10() {
      System.out.println(Math.log10(10.0d));
    }

    @NeverInline
    static void testLog1p() {
      System.out.println(Math.log1p(0.0d));
    }

    @NeverInline
    static void testMaxDouble() {
      System.out.println(Math.max(1.5d, 3.5d));
    }

    @NeverInline
    static void testMaxFloat() {
      System.out.println(Math.max(1.5f, 3.5f));
    }

    @NeverInline
    static void testMaxInt() {
      System.out.println(Math.max(1, 3));
    }

    @NeverInline
    static void testMaxLong() {
      System.out.println(Math.max(1L, 3L));
    }

    @NeverInline
    static void testMinDouble() {
      System.out.println(Math.min(1.5d, 3.5d));
    }

    @NeverInline
    static void testMinFloat() {
      System.out.println(Math.min(1.5f, 3.5f));
    }

    @NeverInline
    static void testMinInt() {
      System.out.println(Math.min(1, 3));
    }

    @NeverInline
    static void testMinLong() {
      System.out.println(Math.min(1L, 3L));
    }

    @NeverInline
    static void testMultiplyExactInt() {
      System.out.println(Math.multiplyExact(2, 3));
    }

    @NeverInline
    static void testMultiplyExactLong() {
      System.out.println(Math.multiplyExact(2L, 3L));
    }

    @NeverInline
    static void testNegateExactInt() {
      System.out.println(Math.negateExact(3));
    }

    @NeverInline
    static void testNegateExactLong() {
      System.out.println(Math.negateExact(3L));
    }

    @NeverInline
    static void testNextAfterDouble() {
      System.out.println(Math.nextAfter(1.0d, 1.0d));
    }

    @NeverInline
    static void testNextAfterFloat() {
      System.out.println(Math.nextAfter(1.0f, 1.0d));
    }

    @NeverInline
    static void testNextDownDouble() {
      System.out.println(Math.nextDown(1.0d));
    }

    @NeverInline
    static void testNextDownFloat() {
      System.out.println(Math.nextDown(1.0f));
    }

    @NeverInline
    static void testNextUpDouble() {
      System.out.println(Math.nextUp(1.0d));
    }

    @NeverInline
    static void testNextUpFloat() {
      System.out.println(Math.nextUp(1.0f));
    }

    @NeverInline
    static void testPow() {
      System.out.println(Math.pow(2.0d, 3.0d));
    }

    @NeverInline
    static void testRint() {
      System.out.println(Math.rint(3.5d));
    }

    @NeverInline
    static void testRoundDouble() {
      System.out.println(Math.round(3.5d));
    }

    @NeverInline
    static void testRoundFloat() {
      System.out.println(Math.round(3.5f));
    }

    @NeverInline
    static void testScalbDouble() {
      System.out.println(Math.scalb(1.0d, 2));
    }

    @NeverInline
    static void testScalbFloat() {
      System.out.println(Math.scalb(1.0f, 2));
    }

    @NeverInline
    static void testSignumDouble() {
      System.out.println(Math.signum(3.5d));
    }

    @NeverInline
    static void testSignumFloat() {
      System.out.println(Math.signum(3.5f));
    }

    @NeverInline
    static void testSin() {
      System.out.println(Math.sin(0.0d));
    }

    @NeverInline
    static void testSinh() {
      System.out.println(Math.sinh(0.0d));
    }

    @NeverInline
    static void testSqrt() {
      System.out.println(Math.sqrt(4.0d));
    }

    @NeverInline
    static void testSubtractExactInt() {
      System.out.println(Math.subtractExact(3, 2));
    }

    @NeverInline
    static void testSubtractExactLong() {
      System.out.println(Math.subtractExact(3L, 2L));
    }

    @NeverInline
    static void testTan() {
      System.out.println(Math.tan(0.0d));
    }

    @NeverInline
    static void testTanh() {
      System.out.println(Math.tanh(0.0d));
    }

    @NeverInline
    static void testToDegrees() {
      System.out.println(Math.toDegrees(Math.PI));
    }

    @NeverInline
    static void testToIntExact() {
      System.out.println(Math.toIntExact(3L));
    }

    @NeverInline
    static void testToRadians() {
      System.out.println(Math.toRadians(0.0d));
    }

    @NeverInline
    static void testUlpDouble() {
      System.out.println(Math.ulp(1.0d));
    }

    @NeverInline
    static void testUlpFloat() {
      System.out.println(Math.ulp(1.0f));
    }

    public static void main(String[] args) {
      testAbsDouble();
      testAbsFloat();
      testAbsInt();
      testAbsLong();
      testAcos();
      testAddExactInt();
      testAddExactLong();
      testAsin();
      testAtan();
      testAtan2();
      testCbrt();
      testCeil();
      testCopySignDouble();
      testCopySignFloat();
      testCos();
      testCosh();
      testDecrementExactInt();
      testDecrementExactLong();
      testExp();
      testExpm1();
      testFloor();
      testFloorDivInt();
      testFloorDivLong();
      testFloorModInt();
      testFloorModLong();
      testGetExponentDouble();
      testGetExponentFloat();
      testHypot();
      testIEEEremainder();
      testIncrementExactInt();
      testIncrementExactLong();
      testLog();
      testLog10();
      testLog1p();
      testMaxDouble();
      testMaxFloat();
      testMaxInt();
      testMaxLong();
      testMinDouble();
      testMinFloat();
      testMinInt();
      testMinLong();
      testMultiplyExactInt();
      testMultiplyExactLong();
      testNegateExactInt();
      testNegateExactLong();
      testNextAfterDouble();
      testNextAfterFloat();
      testNextDownDouble();
      testNextDownFloat();
      testNextUpDouble();
      testNextUpFloat();
      testPow();
      testRint();
      testRoundDouble();
      testRoundFloat();
      testScalbDouble();
      testScalbFloat();
      testSignumDouble();
      testSignumFloat();
      testSin();
      testSinh();
      testSqrt();
      testSubtractExactInt();
      testSubtractExactLong();
      testTan();
      testTanh();
      testToDegrees();
      testToIntExact();
      testToRadians();
      testUlpDouble();
      testUlpFloat();
    }
  }
}
