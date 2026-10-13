// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.ifs;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ShallowAbstractValueBranchSimplificationTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ShallowAbstractValueBranchSimplificationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("whole", "less", "unspecified", "equal", "greater");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());

    for (String methodName :
        new String[] {
          "testIfZeroFloatCmpEqual",
          "testIfZeroFloatCmpOrdered",
          "testIfZeroLongCmpEqual",
          "testNonZeroIfEqual",
          "testNonZeroIfOrdered"
        }) {
      MethodSubject method = classSubject.uniqueMethodWithOriginalName(methodName);
      assertThat(method, isPresent());
      assertTrue(method.streamInstructions().noneMatch(InstructionSubject::isIf));
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      long now = System.currentTimeMillis();
      testIfZeroFloatCmpEqual((float) now, 1);
      testIfZeroFloatCmpOrdered((float) now, 1);
      testIfZeroLongCmpEqual(now, 1);
      testNonZeroIfEqual((int) now, 1);
      testNonZeroIfOrdered((int) now, 1);
    }

    @NeverInline
    static void testIfZeroFloatCmpEqual(float fraction, int mask) {
      if ((mask & 1) != 0) {
        fraction = 1.0f;
      }
      System.out.println(fraction == 1.0f ? "whole" : "custom");
    }

    @NeverInline
    static void testIfZeroFloatCmpOrdered(float fraction, int mask) {
      if ((mask & 1) != 0) {
        fraction = 0.5f;
      }
      System.out.println(fraction < 1.0f ? "less" : "not-less");
    }

    @NeverInline
    static void testIfZeroLongCmpEqual(long color, int mask) {
      if ((mask & 1) != 0) {
        color = 10L;
      }
      System.out.println(color == 10L ? "unspecified" : "specified");
    }

    @NeverInline
    static void testNonZeroIfEqual(int value, int mask) {
      if ((mask & 1) != 0) {
        value = 2;
      }
      System.out.println((value + 1) == 3 ? "equal" : "not-equal");
    }

    @NeverInline
    static void testNonZeroIfOrdered(int value, int mask) {
      if ((mask & 1) != 0) {
        value = 2;
      }
      System.out.println((value + 1) > 2 ? "greater" : "not-greater");
    }
  }
}
