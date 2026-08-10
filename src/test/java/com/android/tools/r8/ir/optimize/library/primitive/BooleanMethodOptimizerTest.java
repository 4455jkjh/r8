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
public class BooleanMethodOptimizerTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "true", // booleanValue
          "1", // compare
          "-1", // compare
          "0", // compare
          "1", // compareTo
          "true", // equals
          "false", // equals
          "1231", // hashCode
          "1231", // static hashCode
          "1237", // static hashCode
          "false", // logicalAnd
          "true", // logicalOr
          "true", // logicalXor
          "true", // parseBoolean
          "false", // parseBoolean
          "true", // toString
          "true", // static toString
          "true", // valueOf(boolean)
          "true"); // valueOf(String)

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
      verifyMethodHasNoBooleanInvokes(mainClass.uniqueMethodWithOriginalName("testBooleanValue"));
    }
    verifyMethodHasNoBooleanInvokes(mainClass.uniqueMethodWithOriginalName("testCompare"));
    verifyMethodHasNoBooleanInvokes(mainClass.uniqueMethodWithOriginalName("testCompareTo"));
    verifyMethodHasNoBooleanInvokes(mainClass.uniqueMethodWithOriginalName("testEquals"));
    verifyMethodHasNoBooleanInvokes(mainClass.uniqueMethodWithOriginalName("testHashCode"));
    verifyMethodHasNoBooleanInvokes(mainClass.uniqueMethodWithOriginalName("testStaticHashCode"));
    verifyMethodHasNoBooleanInvokes(mainClass.uniqueMethodWithOriginalName("testLogicalAnd"));
    verifyMethodHasNoBooleanInvokes(mainClass.uniqueMethodWithOriginalName("testLogicalOr"));
    verifyMethodHasNoBooleanInvokes(mainClass.uniqueMethodWithOriginalName("testLogicalXor"));
    verifyMethodHasNoBooleanInvokes(mainClass.uniqueMethodWithOriginalName("testParseBoolean"));
    verifyMethodHasNoBooleanInvokes(mainClass.uniqueMethodWithOriginalName("testToString"));
    verifyMethodHasNoBooleanInvokes(mainClass.uniqueMethodWithOriginalName("testStaticToString"));
    verifyMethodHasNoBooleanStringValueOfInvokes(
        mainClass.uniqueMethodWithOriginalName("testValueOfString"));
  }

  private void verifyMethodHasNoBooleanInvokes(MethodSubject method) {
    assertThat(method, not(invokesMethodWithHolder(Boolean.class)));
  }

  private void verifyMethodHasNoBooleanStringValueOfInvokes(MethodSubject method) {
    assertTrue(
        method
            .streamInstructions()
            .noneMatch(
                i ->
                    i.isInvokeStatic()
                        && i.getMethod().getHolderType().getName().equals("java.lang.Boolean")
                        && i.getMethod().getName().equals("valueOf")
                        && i.getMethod().getParameter(0).getName().equals("java.lang.String")));
  }

  static class Main {

    @NeverInline
    static void testBooleanValue() {
      System.out.println(Boolean.valueOf(true).booleanValue());
    }

    @NeverInline
    static void testCompare() {
      System.out.println(Boolean.compare(true, false));
      System.out.println(Boolean.compare(false, true));
      System.out.println(Boolean.compare(true, true));
    }

    @NeverInline
    static void testCompareTo() {
      System.out.println(Boolean.valueOf(true).compareTo(Boolean.valueOf(false)));
    }

    @NeverInline
    static void testEquals() {
      System.out.println(Boolean.valueOf(true).equals(Boolean.valueOf(true)));
      System.out.println(Boolean.valueOf(true).equals(Boolean.valueOf(false)));
    }

    @NeverInline
    static void testHashCode() {
      System.out.println(Boolean.valueOf(true).hashCode());
    }

    @NeverInline
    static void testStaticHashCode() {
      System.out.println(Boolean.hashCode(true));
      System.out.println(Boolean.hashCode(false));
    }

    @NeverInline
    static void testLogicalAnd() {
      System.out.println(Boolean.logicalAnd(true, false));
    }

    @NeverInline
    static void testLogicalOr() {
      System.out.println(Boolean.logicalOr(true, false));
    }

    @NeverInline
    static void testLogicalXor() {
      System.out.println(Boolean.logicalXor(true, false));
    }

    @NeverInline
    static void testParseBoolean() {
      System.out.println(Boolean.parseBoolean("true"));
      System.out.println(Boolean.parseBoolean("false"));
    }

    @NeverInline
    static void testToString() {
      System.out.println(Boolean.valueOf(true).toString());
    }

    @NeverInline
    static void testStaticToString() {
      System.out.println(Boolean.toString(true));
    }

    @NeverInline
    static void testValueOfBoolean() {
      System.out.println(Boolean.valueOf(true));
    }

    @NeverInline
    static void testValueOfString() {
      System.out.println(Boolean.valueOf("true"));
    }

    public static void main(String[] args) {
      testBooleanValue();
      testCompare();
      testCompareTo();
      testEquals();
      testHashCode();
      testStaticHashCode();
      testLogicalAnd();
      testLogicalOr();
      testLogicalXor();
      testParseBoolean();
      testToString();
      testStaticToString();
      testValueOfBoolean();
      testValueOfString();
    }
  }
}
