// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.numberunboxing;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
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
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StaticMethodsWideUnboxingMixNumberUnboxingTest extends TestBase {

  private final String EXPECTED_RESULT =
      StringUtils.lines(
          "1", "5", "9", "2", "6", "10", "4", "8", "12", "5", "9", "13", "11", "15", "19", "12",
          "16", "110", "14", "18", "112", "15", "19", "113");

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public StaticMethodsWideUnboxingMixNumberUnboxingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testNumberUnboxing() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .addOptionsModification(opt -> opt.getTestingOptions().getNumberUnboxerOptions().enable())
        .compile()
        .inspect(this::assertUnboxing)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private void assertUnboxing(CodeInspector codeInspector) {
    ClassSubject mainClass = codeInspector.clazz(Main.class);
    assertThat(mainClass, isPresent());

    MethodSubject methodSubject = mainClass.uniqueMethodWithOriginalName("printI");
    assertThat(methodSubject, isPresent());
    assertTrue(methodSubject.getProgramMethod().getParameter(0).isIntType());

    methodSubject = mainClass.uniqueMethodWithOriginalName("printL");
    assertThat(methodSubject, isPresent());
    assertTrue(methodSubject.getProgramMethod().getParameter(0).isLongType());

    methodSubject = mainClass.uniqueMethodWithOriginalName("printAll");
    assertThat(methodSubject, isPresent());
    for (int i = 0; i < 12; i += 2) {
      assertTrue(methodSubject.getProgramMethod().getParameter(i).isIntType());
      assertTrue(methodSubject.getProgramMethod().getParameter(i + 1).isLongType());
    }
  }

  static class Main {

    public static void main(String[] args) {
      printAll(1, 2, 3, 4L, 5, 6, 7, 8L, 9, 10, 11, 12L);
      printAll(11, 12, 13, 14L, 15, 16, 17, 18L, 19, 110, 111, 112L);
    }

    @NeverInline
    private static void printAll(
        int i,
        long l,
        Integer boxedI,
        Long boxedL,
        int i2,
        long l2,
        Integer boxedI2,
        Long boxedL2,
        int i3,
        long l3,
        Integer boxedI3,
        Long boxedL3) {

      System.out.println(i);
      System.out.println(i2);
      System.out.println(i3);
      System.out.println(l);
      System.out.println(l2);
      System.out.println(l3);

      printI(boxedI);
      printI(boxedI2);
      printI(boxedI3);
      printL(boxedL);
      printL(boxedL2);
      printL(boxedL3);
    }

    @NeverInline
    private static void printI(Integer boxed) {
      System.out.println(boxed + 1);
    }

    @NeverInline
    private static void printL(Long boxed) {
      System.out.println(boxed + 1);
    }
  }
}
