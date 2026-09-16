// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.SingleTestRunResult;
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

@RunWith(Parameterized.class)
public class StringCharAtTest extends TestBase {
  private static final String JAVA_OUTPUT =
      StringUtils.lines(
          "h",
          "o",
          "55356",
          "56481",
          "e",
          "l",
          "NPE",
          "SIOOBE negative",
          "SIOOBE at length",
          "SIOOBE out of bounds");
  private static final Class<?> MAIN = TestClass.class;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final TestParameters parameters;

  public StringCharAtTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJVMOutput() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
  }

  private void inspect(
      SingleTestRunResult<?> result,
      int expectedConstantCalls,
      int expectedOutOfBoundsCalls,
      int expectedNonConstantCalls)
      throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);
    assertThat(mainClass, isPresent());

    MethodSubject constantMethod = mainClass.uniqueMethodWithOriginalName("constantCalls");
    assertThat(constantMethod, isPresent());
    assertEquals(expectedConstantCalls, countCall(constantMethod, "String", "charAt"));

    MethodSubject negativeIndexMethod = mainClass.uniqueMethodWithOriginalName("negativeIndex");
    assertThat(negativeIndexMethod, isPresent());
    assertEquals(expectedOutOfBoundsCalls, countCall(negativeIndexMethod, "String", "charAt"));

    MethodSubject indexAtLengthMethod = mainClass.uniqueMethodWithOriginalName("indexAtLength");
    assertThat(indexAtLengthMethod, isPresent());
    assertEquals(expectedOutOfBoundsCalls, countCall(indexAtLengthMethod, "String", "charAt"));

    MethodSubject indexOutOfBoundsMethod =
        mainClass.uniqueMethodWithOriginalName("indexOutOfBounds");
    assertThat(indexOutOfBoundsMethod, isPresent());
    assertEquals(expectedOutOfBoundsCalls, countCall(indexOutOfBoundsMethod, "String", "charAt"));

    MethodSubject nonConstantReceiverMethod =
        mainClass.uniqueMethodWithOriginalName("nonConstantReceiver");
    assertThat(nonConstantReceiverMethod, isPresent());
    assertEquals(
        expectedNonConstantCalls, countCall(nonConstantReceiverMethod, "String", "charAt"));

    MethodSubject nonConstantIndexMethod =
        mainClass.uniqueMethodWithOriginalName("nonConstantIndex");
    assertThat(nonConstantIndexMethod, isPresent());
    assertEquals(expectedNonConstantCalls, countCall(nonConstantIndexMethod, "String", "charAt"));
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend", parameters.isDexRuntime());
    D8TestRunResult result =
        testForD8()
            .release()
            .addProgramClasses(MAIN)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), MAIN);
    result.assertSuccessWithOutput(JAVA_OUTPUT);
    inspect(result, 0, 1, 1);

    result =
        testForD8()
            .debug()
            .addProgramClasses(MAIN)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), MAIN);
    result.assertSuccessWithOutput(JAVA_OUTPUT);
    inspect(result, 4, 1, 1);
  }

  @Test
  public void testR8() throws Exception {
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClasses(MAIN)
            .enableInliningAnnotations()
            .addKeepMainRule(MAIN)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    inspect(result, 0, 1, 1);
  }

  public static class TestClass {

    @NeverInline
    static void constantCalls() {
      System.out.println("hello".charAt(0));
      System.out.println("hello".charAt(4));
      // Surrogate pair for "🂡" (U+1F0A1): high surrogate 55356, low surrogate 56481
      System.out.println((int) "🂡".charAt(0));
      System.out.println((int) "🂡".charAt(1));
    }

    @NeverInline
    static char nonConstantReceiver(String s) {
      return s.charAt(1);
    }

    @NeverInline
    static char nonConstantIndex(int i) {
      return "hello".charAt(i);
    }

    @NeverInline
    static char nullReceiver() {
      String s = System.currentTimeMillis() > 0 ? null : "hello";
      return s.charAt(0);
    }

    @NeverInline
    static char negativeIndex() {
      return "hello".charAt(-1);
    }

    @NeverInline
    static char indexAtLength() {
      return "hello".charAt(5);
    }

    @NeverInline
    static char indexOutOfBounds() {
      return "hello".charAt(10);
    }

    public static void main(String[] args) {
      constantCalls();
      System.out.println(nonConstantReceiver(args.length == 0 ? "hello" : args[0]));
      System.out.println(nonConstantIndex(args.length == 0 ? 2 : args.length));
      try {
        System.out.println(nullReceiver());
      } catch (NullPointerException e) {
        System.out.println("NPE");
      }
      try {
        System.out.println(negativeIndex());
      } catch (StringIndexOutOfBoundsException e) {
        System.out.println("SIOOBE negative");
      }
      try {
        System.out.println(indexAtLength());
      } catch (StringIndexOutOfBoundsException e) {
        System.out.println("SIOOBE at length");
      }
      try {
        System.out.println(indexOutOfBounds());
      } catch (StringIndexOutOfBoundsException e) {
        System.out.println("SIOOBE out of bounds");
      }
    }
  }
}
